package com.bank.trust.service;

import com.bank.trust.event.TrustEventPublisher;
import com.bank.trust.model.TrustViews.TrustScoreChangeView;
import com.bank.trust.model.TrustViews.TrustScoreView;
import com.bank.trust.persistence.TrustRepository;
import com.bank.trust.persistence.TrustRepository.DepositRow;
import com.bank.trust.persistence.TrustRepository.ScoreRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Trust scoring rules from v2 §3.6.
 *
 * <ul>
 *   <li>{@code MoneyDeposited}: +0.5 per ₹10,000, capped +5/day</li>
 *   <li>{@code LoanRepaidOnTime}: +10, {@code LoanRepaidEarly}: +15</li>
 *   <li>{@code LoanDisbursed}/{@code LoanTaken}: −5</li>
 *   <li>{@code LoanRepaidLate}: −8, {@code LoanDefaulted}: −20</li>
 * </ul>
 *
 * Scores are integers, so the documented half-point deposit rate accumulates in
 * {@code deposit_daily}: one point per ₹20,000 deposited that day, five max.
 * Tiers: 0–40 Low, 41–70 Medium, 71–90 High, 91–100 Excellent.
 */
@Service
public class TrustScoreService {

    private static final Logger log = LoggerFactory.getLogger(TrustScoreService.class);

    private static final int DEPOSIT_DAILY_CAP = 5;

    private final TrustRepository repository;
    private final TrustEventPublisher publisher;
    private final ObjectMapper objectMapper;

    public TrustScoreService(TrustRepository repository, TrustEventPublisher publisher,
                             ObjectMapper objectMapper) {
        this.repository = repository;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------ reads

    public TrustScoreView get(UUID accountId) {
        return repository.findScore(accountId)
                .map(repository::view)
                .orElseThrow(() -> new TrustExceptions.NotFound(
                        "No trust score for account " + accountId));
    }

    public List<TrustScoreChangeView> changes(UUID accountId, int limit) {
        return repository.listChanges(accountId, limit);
    }

    // ------------------------------------------------------------------ events

    /** Apply one Kafka envelope (account.events or loan.events). Idempotent. */
    @Transactional
    public void apply(String topic, String envelopeJson) {
        if (envelopeJson == null || envelopeJson.isBlank()) {
            return;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(envelopeJson);
        } catch (Exception e) {
            log.warn("Ignoring malformed {} event: {}", topic, e.getMessage());
            return;
        }
        String type = text(root, "eventType");
        if (type == null) {
            type = text(root, "type");
        }
        if (type == null) {
            return;
        }
        JsonNode payload = root.path("payload");
        String accountText = text(payload, "accountId");
        if (accountText == null) {
            accountText = text(root, "aggregateId");
        }
        if (accountText == null) {
            return;
        }
        UUID accountId;
        try {
            accountId = UUID.fromString(accountText);
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring {} event with bad accountId {}", type, accountText);
            return;
        }
        String eventIdText = text(root, "eventId");
        UUID eventId = eventIdText == null
                ? UUID.nameUUIDFromBytes(envelopeJson.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                : UUID.fromString(eventIdText);
        if (!repository.claimEvent(eventId, topic)) {
            log.debug("Duplicate {} event {}", type, eventId);
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        repository.ensureAccount(accountId, now);
        String reason = reason(type);
        if (reason == null) {
            return;
        }
        if ("MoneyDeposited".equals(type)) {
            applyDeposit(accountId, amount(payload), now);
            return;
        }
        int delta = switch (type) {
            case "LoanRepaidOnTime" -> 10;
            case "LoanRepaidEarly" -> 15;
            case "LoanDisbursed", "LoanTaken" -> -5;
            case "LoanRepaidLate" -> -8;
            case "LoanDefaulted" -> -20;
            default -> 0;
        };
        applyDelta(accountId, delta, reason, now);
    }

    private void applyDeposit(UUID accountId, BigDecimal amount, OffsetDateTime now) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        LocalDate day = now.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
        DepositRow current = repository.lockDailyDeposits(accountId, day);
        BigDecimal total = current.totalDeposited().add(amount);
        int rawPoints = total
                .divide(BigDecimal.valueOf(20000), 0, RoundingMode.DOWN)
                .intValue();
        int capped = Math.min(rawPoints, DEPOSIT_DAILY_CAP);
        int award = Math.max(0, capped - current.pointsAwarded());
        repository.updateDailyDeposits(accountId, day, total, capped);
        if (award > 0) {
            applyDelta(accountId, award, "DEPOSIT", now);
        }
    }

    private void applyDelta(UUID accountId, int delta, String reason, OffsetDateTime now) {
        if (delta == 0) {
            return;
        }
        ScoreRow row = repository.lockScore(accountId).orElse(null);
        if (row == null) {
            return;
        }
        int before = row.score();
        int after = Math.max(0, Math.min(100, before + delta));
        if (after == before) {
            return;
        }
        int tierBefore = tierOf(before);
        int tierAfter = tierOf(after);
        repository.updateScore(accountId, after, tierAfter, now);

        TrustScoreChangeView change = new TrustScoreChangeView(
                UUID.randomUUID(), accountId, before, after, tierBefore, tierAfter,
                reason, now);
        repository.insertChange(change);
        publisher.publish(change);
    }

    // ------------------------------------------------------------------ helpers

    static int tierOf(int score) {
        if (score >= 91) {
            return 3;
        }
        if (score >= 71) {
            return 2;
        }
        if (score >= 41) {
            return 1;
        }
        return 0;
    }

    private static String reason(String eventType) {
        return switch (eventType) {
            case "MoneyDeposited" -> "DEPOSIT";
            case "LoanRepaidOnTime", "LoanRepaidEarly", "LoanRepaidLate" -> "LOAN_REPAYMENT";
            case "LoanDisbursed", "LoanTaken" -> "LOAN_TAKEN";
            case "LoanDefaulted" -> "LOAN_DEFAULT";
            default -> null;
        };
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
    public static final class TrustExceptions {
        private TrustExceptions() {
        }

        public static class NotFound extends RuntimeException {
            public NotFound(String message) {
                super(message);
            }
        }
    }
}
