package com.bank.ledgerquery.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Direct read access to the three regional event-store shards.
 *
 * <p>The read model is served from Mongo/FerretDB; this class is only used for
 * (a) per-account event listings, which the contract says come from the sharded
 * ledger, and (b) full replay/rebuild of the read model. The application-level
 * region router replaces the planned ShardingSphere-JDBC dependency; see
 * {@code docs/FINAL_PLAN.md} and ADR-004.</p>
 */
@Component
public class ShardedLedger {

    private static final Logger log = LoggerFactory.getLogger(ShardedLedger.class);
    public static final List<String> REGIONS = List.of("APAC", "EU", "AMER");

    public record StoredLedgerEvent(UUID eventId, UUID aggregateId, int version, String eventType,
                                    String payloadJson, String region, String correlationId,
                                    String prevHash, String hash, OffsetDateTime occurredAt) {
    }

    private final Map<String, HikariDataSource> dataSources = new LinkedHashMap<>();
    private final Map<String, JdbcTemplate> templates = new LinkedHashMap<>();
    private final Map<String, String> urls = new LinkedHashMap<>();
    private final JdbcTemplate coordinator;

    public ShardedLedger(JdbcTemplate coordinator,
                         @Value("${app.shards.APAC}") String apac,
                         @Value("${app.shards.EU}") String eu,
                         @Value("${app.shards.AMER}") String amer,
                         @Value("${app.db.username:ddbs}") String user,
                         @Value("${app.db.password:ddbs}") String password) {
        this.coordinator = coordinator;
        Map<String, String> shardUrls = Map.of("APAC", apac, "EU", eu, "AMER", amer);
        for (String region : REGIONS) {
            urls.put(region, shardUrls.get(region));
            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl(shardUrls.get(region));
            cfg.setUsername(user);
            cfg.setPassword(password);
            cfg.setMaximumPoolSize(3);
            cfg.setConnectionTimeout(3_000);
            cfg.setPoolName("query-shard-" + region.toLowerCase());
            HikariDataSource ds = new HikariDataSource(cfg);
            dataSources.put(region, ds);
            templates.put(region, new JdbcTemplate(ds));
        }
    }

    public List<StoredLedgerEvent> accountEvents(UUID accountId) {
        String region = directoryRegion(accountId);
        List<String> order = new ArrayList<>();
        if (region != null) {
            order.add(region);
        }
        for (String candidate : REGIONS) {
            if (!candidate.equals(region)) {
                order.add(candidate);
            }
        }
        for (String candidate : order) {
            List<StoredLedgerEvent> events = query(candidate, accountId);
            if (!events.isEmpty()) {
                return events;
            }
        }
        return List.of();
    }

    public List<StoredLedgerEvent> allEvents() {
        List<StoredLedgerEvent> all = new ArrayList<>();
        for (String region : REGIONS) {
            try {
                all.addAll(templates.get(region).query("""
                                SELECT event_id, aggregate_id, version, event_type, payload::text AS payload,
                                       region, correlation_id, prev_hash, hash, occurred_at
                                FROM ledger_events
                                ORDER BY occurred_at ASC, version ASC
                                """,
                        (rs, n) -> new StoredLedgerEvent(
                                rs.getObject("event_id", UUID.class),
                                rs.getObject("aggregate_id", UUID.class),
                                rs.getInt("version"),
                                rs.getString("event_type"),
                                rs.getString("payload"),
                                rs.getString("region"),
                                rs.getString("correlation_id"),
                                rs.getString("prev_hash"),
                                rs.getString("hash"),
                                rs.getObject("occurred_at", OffsetDateTime.class))));
            } catch (RuntimeException e) {
                log.warn("Replay could not read shard {}: {}", region, e.getMessage());
            }
        }
        all.sort((a, b) -> a.occurredAt().compareTo(b.occurredAt()));
        return all;
    }

    public List<Map<String, Object>> stats() {
        return REGIONS.stream().map(region -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("shardId", "ds_" + region.toLowerCase());
            row.put("region", region);
            row.put("host", hostOf(region));
            row.put("port", portOf(region));
            row.put("status", "UP");
            Map<String, Object> counts = new LinkedHashMap<>();
            for (String table : List.of("ledger_events", "account_core", "account_pii")) {
                try {
                    Long count = templates.get(region).queryForObject(
                            "SELECT count(*) FROM " + table, Long.class);
                    counts.put(table, count == null ? 0 : count);
                } catch (RuntimeException e) {
                    counts.put(table, 0);
                    row.put("status", "DEGRADED");
                }
            }
            row.put("rowCounts", counts);
            return row;
        }).toList();
    }

    private List<StoredLedgerEvent> query(String region, UUID accountId) {
        try {
            return templates.get(region).query("""
                            SELECT event_id, aggregate_id, version, event_type, payload::text AS payload,
                                   region, correlation_id, prev_hash, hash, occurred_at
                            FROM ledger_events
                            WHERE aggregate_id = ?
                            ORDER BY version ASC
                            """,
                    (rs, n) -> new StoredLedgerEvent(
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
        } catch (RuntimeException e) {
            log.warn("Could not read shard {}: {}", region, e.getMessage());
            return List.of();
        }
    }

    private String directoryRegion(UUID accountId) {
        try {
            List<String> rows = coordinator.query(
                    "SELECT region FROM account_command.account_directory WHERE account_id = ?",
                    (rs, n) -> rs.getString(1), accountId);
            return rows.stream().findFirst().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String hostOf(String region) {
        try {
            java.net.URI uri = java.net.URI.create(configuredUrl(region).substring("jdbc:".length()));
            return uri.getHost();
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    private int portOf(String region) {
        try {
            java.net.URI uri = java.net.URI.create(configuredUrl(region).substring("jdbc:".length()));
            return uri.getPort() == -1 ? 5432 : uri.getPort();
        } catch (RuntimeException e) {
            return 5432;
        }
    }

    private String configuredUrl(String region) {
        return urls.getOrDefault(region, "jdbc:postgresql://localhost:5433/ddbs_" + region.toLowerCase());
    }

    @PreDestroy
    public void close() {
        dataSources.values().forEach(HikariDataSource::close);
    }
}
