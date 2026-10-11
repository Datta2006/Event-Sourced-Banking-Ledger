package com.bank.fraud.service;

import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for detecting fraudulent transactions.
 * Implements requirements from HLD_LLD_SystemDesign_v2.md:
 * - Fraud rules over account/payment events
 *
 * In-memory case store: this slice has no database by design
 * (compose: "Fraud Detection Service — no persistence this slice").
 * Rules implemented:
 * - VELOCITY_3_WITHDRAWALS_60S: 3+ withdrawals per account within 60s
 * - LARGE_WITHDRAWAL: single withdrawal above the threshold
 */
@Service
public class FraudDetectionService {

    private static final double LARGE_WITHDRAWAL_THRESHOLD = 200000; // ₹2,00,000
    private static final int VELOCITY_WINDOW_MILLIS = 60_000;
    private static final int VELOCITY_COUNT = 3;

    /** Withdrawal timestamps per account, for the velocity rule. */
    private final Map<UUID, List<Long>> withdrawalTimes = new ConcurrentHashMap<>();

    /** Case store, newest first. */
    private final List<Map<String, Object>> cases = new ArrayList<>();

    private static final Pattern AMOUNT =
            Pattern.compile("\"amount\"\\s*:\\s*([0-9.]+)");
    private static final Pattern AGGREGATE_ID =
            Pattern.compile("\"aggregateId\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * Analyze an account event; opens a case when a rule fires.
     * Called by the Kafka consumer for account.events.
     */
    public boolean analyzeEventForFraud(String eventType, String payload) {
        if (payload == null || payload.isBlank()) return false;

        if ("MoneyWithdrawn".equals(eventType)) {
            UUID accountId = extractAccountId(payload);
            Double amount = extractAmount(payload);
            if (accountId == null || amount == null) return false;

            long now = System.currentTimeMillis();
            List<Long> times = withdrawalTimes.computeIfAbsent(accountId, k ->
                    java.util.Collections.synchronizedList(new ArrayList<>()));
            synchronized (times) {
                times.removeIf(t -> now - t > VELOCITY_WINDOW_MILLIS);
                times.add(now);
            }
            boolean opened = false;

            // Rule 1: velocity — 3+ withdrawals inside a minute
            int inWindow;
            synchronized (times) {
                inWindow = times.size();
            }
            if (inWindow >= VELOCITY_COUNT) {
                openCase(accountId, "VELOCITY_3_WITHDRAWALS_60S",
                        Math.min(95, 50 + inWindow * 10), eventType);
                withdrawalTimes.put(accountId,
                        java.util.Collections.synchronizedList(new ArrayList<>())); // reset window
                opened = true;
            }

            // Rule 2: single unusually large withdrawal
            if (amount > LARGE_WITHDRAWAL_THRESHOLD) {
                openCase(accountId, "LARGE_WITHDRAWAL",
                        amount > LARGE_WITHDRAWAL_THRESHOLD * 2 ? 88 : 70, eventType);
                opened = true;
            }
            return opened;
        }
        return false;
    }

    /**
     * Case list for the UI (GET /api/fraud/cases via the gateway).
     */
    public List<Map<String, Object>> listCases(UUID accountId, String status) {
        synchronized (cases) {
            return cases.stream()
                    .filter(c -> accountId == null || accountId.toString().equals(c.get("accountId")))
                    .filter(c -> status == null || status.equals(c.get("status")))
                    .sorted(Comparator.comparing((Map<String, Object> c) -> (String) c.get("flaggedAt")).reversed())
                    .map(c -> new java.util.LinkedHashMap<>(c))
                    .toList();
        }
    }

    /**
     * Review a case: REVIEWED or DISMISSED (POST /fraud/cases/{id}/review).
     */
    public Map<String, Object> reviewCase(String caseId, String status, String note) {
        synchronized (cases) {
            for (Map<String, Object> c : cases) {
                if (caseId.equals(c.get("caseId"))) {
                    if (!"OPEN".equals(c.get("status"))) {
                        throw new IllegalStateException("Case not OPEN");
                    }
                    c.put("status", status);
                    c.put("reviewedAt", OffsetDateTime.now().toString());
                    if (note != null) c.put("note", note);
                    return new java.util.LinkedHashMap<>(c);
                }
            }
        }
        throw new IllegalArgumentException("Case not found: " + caseId);
    }

    private void openCase(UUID accountId, String rule, int riskScore, String triggeredByEvent) {
        Map<String, Object> c = Map.ofEntries(
                Map.entry("caseId", UUID.randomUUID().toString()),
                Map.entry("accountId", accountId.toString()),
                Map.entry("rule", rule),
                Map.entry("riskScore", riskScore),
                Map.entry("triggeredByEvent", triggeredByEvent),
                Map.entry("status", "OPEN"),
                Map.entry("flaggedAt", OffsetDateTime.now().toString()));
        synchronized (cases) {
            cases.add(0, c);
            if (cases.size() > 200) cases.remove(cases.size() - 1);
        }
    }

    private UUID extractAccountId(String payload) {
        Matcher m = AGGREGATE_ID.matcher(payload);
        return m.find() ? UUID.fromString(m.group(1)) : null;
    }

    private Double extractAmount(String payload) {
        Matcher m = AMOUNT.matcher(payload);
        return m.find() ? Double.parseDouble(m.group(1)) : null;
    }
}
