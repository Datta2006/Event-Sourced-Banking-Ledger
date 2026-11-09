package com.bank.accountcmd.event;

import com.bank.accountcmd.config.ShardRouter;
import com.bank.accountcmd.domain.AccountExceptions;
import com.bank.accountcmd.domain.AccountState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only event store with a per-aggregate SHA-256 hash chain.
 *
 * <p>Optimistic concurrency: {@code UNIQUE(aggregate_id, version)} is the ultimate
 * correctness guard (v2 §6). A duplicate-key race is retried once and then surfaced as
 * a 409, exactly as required by the brief.</p>
 *
 * <p>Tamper evidence: every event stores {@code prev_hash} and {@code hash}; the hash
 * covers the previous hash, identity, version, type, canonical payload and correlation
 * id. {@link #verify(UUID)} recomputes the chain and reports the first broken link.</p>
 */
@Component
public class EventStore {

    public record Draft(String eventType, Map<String, Object> payload) {
    }

    public record AuditVerification(UUID accountId, boolean valid, int eventCount,
                                    Integer brokenAtVersion, String expectedHash,
                                    String actualHash) {
    }

    private final ShardRouter router;
    private final ObjectMapper objectMapper;
    private final ObjectMapper canonicalMapper;

    public EventStore(ShardRouter router, ObjectMapper objectMapper) {
        this.router = router;
        this.objectMapper = objectMapper;
        this.canonicalMapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public AccountState fold(UUID accountId) {
        AccountState state = AccountState.empty(accountId);
        for (StoredEvent event : load(accountId)) {
            JsonNode payload = readTree(event.payloadJson());
            state.apply(event.eventType(), payload);
            state.setLastVersion(event.version());
        }
        return state;
    }

    public List<StoredEvent> load(UUID accountId) {
        String region = router.regionOf(accountId);
        return loadFromShard(region, accountId);
    }

    public List<StoredEvent> loadFromShard(String region, UUID accountId) {
        JdbcTemplate jdbc = router.jdbc(region);
        return jdbc.query("""
                        SELECT event_id, aggregate_id, version, event_type, payload::text AS payload,
                               region, correlation_id, prev_hash, hash, occurred_at
                        FROM ledger_events
                        WHERE aggregate_id = ?
                        ORDER BY version ASC
                        """,
                (rs, rowNum) -> new StoredEvent(
                        rs.getObject("event_id", UUID.class),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getInt("version"),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getString("region"),
                        rs.getString("correlation_id"),
                        rs.getString("prev_hash"),
                        rs.getString("hash"),
                        rs.getObject("occurred_at", OffsetDateTime.class)),
                accountId);
    }

    public List<StoredEvent> appendMany(UUID accountId, String region, List<Draft> drafts, String correlationId) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return router.tx(region).execute(status ->
                        appendManyWithin(status, accountId, region, drafts, correlationId));
            } catch (DuplicateKeyException conflict) {
                if (attempt == 1) {
                    throw new AccountExceptions.Conflict(
                            "Optimistic concurrency conflict: another writer advanced aggregate " + accountId);
                }
            }
        }
        throw new AccountExceptions.Conflict("Optimistic concurrency conflict");
    }

    /** Append inside a caller-owned transaction (used to co-write account fragments). */
    public List<StoredEvent> appendManyWithin(TransactionStatus status, UUID accountId, String region,
                                              List<Draft> drafts, String correlationId) {
        JdbcTemplate jdbc = router.jdbc(region);
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version), -1) FROM ledger_events WHERE aggregate_id = ?",
                Integer.class, accountId);
        int version = max == null ? -1 : max;
        String prevHash = jdbc.query(
                "SELECT hash FROM ledger_events WHERE aggregate_id = ? ORDER BY version DESC LIMIT 1",
                (rs, n) -> rs.getString(1), accountId).stream().findFirst().orElse("GENESIS");

        List<StoredEvent> appended = new ArrayList<>();
        for (Draft draft : drafts) {
            version++;
            UUID eventId = UUID.randomUUID();
            OffsetDateTime occurredAt = OffsetDateTime.now();
            String payloadJson = toJson(draft.payload());
            String hash = hash(prevHash, eventId, accountId, version, draft.eventType(),
                    payloadJson, occurredAt, correlationId);
            jdbc.update("""
                            INSERT INTO ledger_events
                              (event_id, aggregate_id, version, event_type, payload, region,
                               correlation_id, prev_hash, hash, occurred_at)
                            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                            """,
                    eventId, accountId, version, draft.eventType(), payloadJson,
                    ShardRouter.normalize(region), correlationId, prevHash, hash, occurredAt);
            StoredEvent event = new StoredEvent(eventId, accountId, version, draft.eventType(),
                    payloadJson, ShardRouter.normalize(region), correlationId, prevHash, hash, occurredAt);
            appended.add(event);
            prevHash = hash;
        }
        return appended;
    }

    public AuditVerification verify(UUID accountId) {
        List<StoredEvent> events = load(accountId);
        String prevHash = "GENESIS";
        for (StoredEvent event : events) {
            String expected = hash(prevHash, event.eventId(), event.aggregateId(), event.version(),
                    event.eventType(), event.payloadJson(), event.occurredAt(), event.correlationId());
            if (!prevHash.equals(event.prevHash()) || !expected.equals(event.hash())) {
                return new AuditVerification(accountId, false, events.size(), event.version(),
                        expected, event.hash());
            }
            prevHash = event.hash();
        }
        return new AuditVerification(accountId, true, events.size(), null, null, null);
    }

    public String hash(String prevHash, UUID eventId, UUID aggregateId, int version, String type,
                       String payloadJson, OffsetDateTime occurredAt, String correlationId) {
        String material = String.join("|",
                prevHash == null ? "GENESIS" : prevHash,
                eventId.toString(),
                aggregateId.toString(),
                Integer.toString(version),
                type,
                canonicalJson(payloadJson),
                occurredAt.toInstant().toString(),
                correlationId == null ? "" : correlationId);
        return sha256(material);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public String canonicalJson(String json) {
        try {
            Object value = canonicalMapper.readValue(json, Object.class);
            return canonicalMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid event payload JSON", e);
        }
    }

    public String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialise event payload", e);
        }
    }

    public JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid event payload", e);
        }
    }

    public Map<String, Object> payloadOf(StoredEvent event) {
        try {
            return objectMapper.readValue(event.payloadJson(), new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }
}
