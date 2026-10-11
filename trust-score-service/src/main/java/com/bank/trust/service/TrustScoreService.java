package com.bank.trust.service;

import com.bank.trust.persistence.TrustScore;
import com.bank.trust.persistence.TrustScoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Service maintaining trust scores for accounts.
 * Implements requirements from HLD_LLD_SystemDesign_v2.md §3.6:
 * - Maintains 0-100 score per account
 * - Updates score based on various events
 * - Publishes TrustScoreChanged events
 */
@Service
public class TrustScoreService {

    @Autowired
    private TrustScoreRepository trustScoreRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Initialize or get trust score for an account.
     */
    @Transactional
    public TrustScore getOrCreateTrustScore(UUID accountId) {
        return trustScoreRepository.findById(accountId)
                .orElseGet(() -> {
                    TrustScore trustScore = new TrustScore(
                            accountId, 50, (short) 1, OffsetDateTime.now()); // start Medium
                    return trustScoreRepository.save(trustScore);
                });
    }

    @Transactional
    public TrustScore getOrCreateTrustScore(String accountId) {
        return getOrCreateTrustScore(UUID.fromString(accountId));
    }

    /**
     * Update trust score based on an event.
     * Implements the scoring rules from HLD_LLD_SystemDesign_v2.md §3.6.
     */
    @Transactional
    public TrustScore updateTrustScore(UUID accountId, String eventType, BigDecimal amount) {
        TrustScore trustScore = getOrCreateTrustScore(accountId);
        int previousScore = trustScore.getScore();
        short previousTier = trustScore.getTier();
        String cause = eventType;
        int scoreChange = 0;

        // Apply scoring rules based on event type
        switch (eventType) {
            case "MoneyDeposited":
                // +0.5 per ₹10,000 deposited (capped +5/day)
                // For simplicity, we'll ignore the daily cap in this scaffold
                if (amount != null) {
                    scoreChange = amount.divide(BigDecimal.valueOf(10000)).multiply(BigDecimal.valueOf(5)).intValue();
                    // Cap at 5 for this transaction (simplifying the daily cap)
                    if (scoreChange > 5) scoreChange = 5;
                }
                break;

            case "LoanRepaidOnTime":
                scoreChange = 10;
                break;

            case "LoanRepaidEarly":
                scoreChange = 15;
                break;

            case "LoanDisbursed": // LoanTaken (disbursed)
                scoreChange = -5;
                break;

            case "LoanRepaidLate":
                scoreChange = -8;
                break;

            case "LoanDefaulted":
                scoreChange = -20;
                break;

            default:
                // Unknown event type, no score change
                break;
        }

        // Apply the score change
        int newScore = Math.max(0, Math.min(100, previousScore + scoreChange));
        trustScore = saveScore(trustScore, newScore);

        // Publish TrustScoreChanged event
        String trustScoreChangedEvent = String.format(
                "{\"accountId\":\"%s\",\"previousScore\":%d,\"newScore\":%d,\"previousTier\":%d,\"newTier\":%d,\"cause\":\"%s\",\"occurredAt\":\"%s\"}",
                accountId,
                previousScore,
                newScore,
                previousTier,
                trustScore.getTier(),
                cause,
                OffsetDateTime.now()
        );
        kafkaTemplate.send("trust-score.events", accountId.toString(), trustScoreChangedEvent);
        return trustScore;
    }

    /**
     * Immutable entity: apply a new score/tier via direct persistence update.
     */
    private TrustScore saveScore(TrustScore current, int newScore) {
        short newTier;
        if (newScore >= 91) newTier = 3;
        else if (newScore >= 71) newTier = 2;
        else if (newScore >= 41) newTier = 1;
        else newTier = 0;
        trustScoreRepository.updateScore(current.getAccountId(), newScore, newTier, OffsetDateTime.now());
        return new TrustScore(current.getAccountId(), newScore, newTier, OffsetDateTime.now());
    }
}
