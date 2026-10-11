package com.bank.loan.kafka;

import com.bank.loan.service.LoanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for processing events relevant to the loan service.
 * Listens to trust-score.events to update local trust score cache.
 */
@Component
public class LoanEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(LoanEventConsumer.class);

    private final LoanService loanService;

    public LoanEventConsumer(LoanService loanService) {
        this.loanService = loanService;
    }

    /**
     * Process trust score events to maintain local cache.
     * In a full implementation, this would update a local cache
     * that the loan service uses for prioritization.
     */
    @KafkaListener(topics = "trust-score.events", groupId = "loan-service")
    public void processTrustScoreEvent(Object event) {
        logger.info("Received trust score event: {}", event);
        // In a full implementation, we would update a local trust score cache here
        // For now, we just log the event
    }

    /**
     * Process loan events for monitoring/auditing.
     */
    @KafkaListener(topics = "loan.events", groupId = "loan-service")
    public void processLoanEvent(Object event) {
        logger.info("Received loan event: {}", event);
        // For monitoring/auditing purposes
    }
}