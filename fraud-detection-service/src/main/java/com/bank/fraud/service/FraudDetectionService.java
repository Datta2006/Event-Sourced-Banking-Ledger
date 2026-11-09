package com.bank.fraud.service;

import com.bank.fraud.event.FraudEventPublisher;
import com.bank.fraud.model.FraudCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fraud rules over account/payment events (carried over from v1).
 *
 * <p>This slice keeps cases in memory by design (see DATA_MODEL §6: Fraud
 * Detection and Notification own no tables). Rules:</p>
 * <ul>
 *   <li>{@code VELOCITY_3_WITHDRAWALS_60S} — 3+ withdrawals per account within
 *       the configured window; risk rises with the count.</li>
 *   <li>{@code LARGE_WITHDRAWAL} — a single withdrawal above ₹2,00,000.</li>
 * </ul>
 */
@Service
public class FraudDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);

    private final FraudEventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final BigDecimal largeWithdrawalThreshold;
    private final long velocityWindowMillis;
    private final int velocityCount;
    private final int maxCases;

    private final Map<UUID, Deque<Long>> withdrawalTimes = new ConcurrentHashMap<>();
    private final List<FraudCase> cases = new ArrayList<>();
    private final Map<String, Boolean> processedEvents = new LinkedHashMap<>();
    private final Map<String, FraudCase> reviewedByKey = new ConcurrentHashMap<>();

    public FraudDetectionService(
            FraudEventPublisher publisher,
            ObjectMapper objectMapper,
            @Value("${app.fraud.large-withdrawal-threshold:200000}") BigDecimal largeWithdrawalThreshold,
            @Value("${app.fraud.velocity-window-seconds:60}") long velocityWindowSeconds,
            @Value("${app.fraud.velocity-count:3}") int velocityCount,
            @Value("${app.fraud.max-cases:500}") int maxCases) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.largeWithdrawalThreshold = largeWithdrawalThreshold;
        this.velocityWindowMillis = velocityWindowSeconds * 1000;
        this.velocityCount = velocityCount;
        this.maxCases = maxCases;
    }

    // ------------------------------------------------------------------ rules

    /** Evaluate one Kafka envelope. Returns true when at least one case opened. */
    public boolean analyze(String topic, String envelopeJson) {
        if (envelopeJson == null || envelopeJson.isBlank()) {
            return false;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(envelopeJson);
        } catch (Exception e) {
            log.warn("Ignoring malformed {} event: {}", topic, e.getMessage());
            return false;
        }
        String type = text(root, "eventType");
        if (type == null) {
            type = text(root, "type");
        }
        if (!"MoneyWithdrawn".equals(type)) {
            return false;
        }
        JsonNode payload = root.path("payload");
        String accountText = text(payload, "accountId");
        if (accountText == null) {
            accountText = text(root, "aggregateId");
        }
        if (accountText == null) {
            return false;
        }
        UUID accountId;
        try {
            accountId = UUID.fromString(accountText);
        } catch (IllegalArgumentException e) {
            return false;
        }
        BigDecimal amount = amount(payload);
        if (amount == null) {
            return false;
        }
        String eventId = text(root, "eventId");
        if (eventId == null) {
            eventId = UUID.nameUUIDFromBytes(envelopeJson.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .toString();
        }
        synchronized (processedEvents) {
            if (processedEvents.containsKey(eventId)) {
                return false;
            }
            processedEvents.put(eventId, Boolean.TRUE);
            if (processedEvents.size() > 10_000) {
                var iterator = processedEvents.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
        }

        boolean opened = false;
        long now = System.currentTimeMillis();
        Deque<Long> times = withdrawalTimes.computeIfAbsent(accountId,
                key -> new ArrayDeque<>());
        int inWindow;
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > velocityWindowMillis) {
                times.pollFirst();
            }
            times.addLast(now);
            inWindow = times.size();
            if (inWindow >= velocityCount) {
                times.clear();
            }
        }
        if (inWindow >= velocityCount) {
            opened |= openCase(accountId, eventId, "VELOCITY_3_WITHDRAWALS_60S",
                    Math.min(95, 50 + inWindow * 10), type);
        }
        if (amount.compareTo(largeWithdrawalThreshold) > 0) {
            opened |= openCase(accountId, eventId, "LARGE_WITHDRAWAL",
                    amount.compareTo(largeWithdrawalThreshold.multiply(BigDecimal.valueOf(2))) > 0 ? 88 : 70,
                    type);
        }
        return opened;
    }

    // ------------------------------------------------------------------ queries

    public List<FraudCase> listCases(UUID accountId, String status) {
        String wanted = status == null || status.isBlank() ? null : status.toUpperCase();
        synchronized (cases) {
            return cases.stream()
                    .filter(c -> accountId == null || accountId.equals(c.accountId()))
                    .filter(c -> wanted == null || wanted.equals(c.status()))
                    .toList();
        }
    }

    public FraudCase getCase(UUID caseId) {
        synchronized (cases) {
            return cases.stream()
                    .filter(c -> caseId.equals(c.caseId()))
                    .findFirst()
                    .orElseThrow(() -> new FraudExceptions.NotFound("Fraud case not found: " + caseId));
        }
    }

    public FraudCase review(UUID caseId, String status, String note, String idempotencyKey) {
        String wanted = status == null ? "" : status.toUpperCase();
        if (!"REVIEWED".equals(wanted) && !"DISMISSED".equals(wanted)) {
            throw new FraudExceptions.BadRequest("status must be REVIEWED or DISMISSED");
        }
        String key = idempotencyKey == null || idempotencyKey.isBlank()
                ? caseId + ":" + wanted : idempotencyKey;
        FraudCase replay = reviewedByKey.get(key);
        if (replay != null) {
            return replay;
        }
        synchronized (cases) {
            for (int i = 0; i < cases.size(); i++) {
                FraudCase current = cases.get(i);
                if (!caseId.equals(current.caseId())) {
                    continue;
                }
                if (!"OPEN".equals(current.status())) {
                    throw new FraudExceptions.Conflict("Case is already " + current.status());
                }
                FraudCase reviewed = current.reviewed(wanted, OffsetDateTime.now());
                cases.set(i, reviewed);
                publisher.caseReviewed(reviewed, note);
                reviewedByKey.put(key, reviewed);
                return reviewed;
            }
        }
        throw new FraudExceptions.NotFound("Fraud case not found: " + caseId);
    }

    // ------------------------------------------------------------------ helpers

    private boolean openCase(UUID accountId, String sourceEventId, String rule,
                             int riskScore, String triggeredByEvent) {
        FraudCase c = new FraudCase(UUID.randomUUID(), accountId, rule, riskScore,
                triggeredByEvent, "OPEN", OffsetDateTime.now(), null);
        synchronized (cases) {
            cases.add(0, c);
            while (cases.size() > maxCases) {
                cases.remove(cases.size() - 1);
            }
        }
        publisher.caseOpened(c);
        log.info("Fraud case {} opened: {} for {} (event {})",
                c.caseId(), rule, accountId, sourceEventId);
        return true;
    }

    private static BigDecimal amount(JsonNode payload) {
        JsonNode node = payload.get("amount");
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return node.isNumber() ? node.decimalValue() : new BigDecimal(node.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /** Domain failures surfaced by the controller advice. */
    public static final class FraudExceptions {
        private FraudExceptions() {
        }

        public static class NotFound extends RuntimeException {
            public NotFound(String message) {
                super(message);
            }
        }

        public static class Conflict extends RuntimeException {
            public Conflict(String message) {
                super(message);
            }
        }

        public static class BadRequest extends RuntimeException {
            public BadRequest(String message) {
                super(message);
            }
        }
    }
}
