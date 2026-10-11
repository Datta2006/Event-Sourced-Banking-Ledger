package com.bank.ledgerquery.kafka;

import com.bank.ledgerquery.service.LedgerQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for processing ledger events to update the read model.
 * Listens to account.events, loan.events, payment.events, and trust-score.events
 * to update the read-optimized data structures for querying.
 */
@Component
public class LedgerEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(LedgerEventConsumer.class);

    private final LedgerQueryService ledgerQueryService;

    public LedgerEventConsumer(LedgerQueryService ledgerQueryService) {
        this.ledgerQueryService = ledgerQueryService;
    }

    /**
     * Process account events to update the read model.
     */
    @KafkaListener(topics = "account.events", groupId = "ledger-query-service")
    public void processAccountEvent(Object event) {
        logger.debug("Received account event for read model update: {}", event);
        // In a full implementation, we would update the read model here
        ledgerQueryService.processLedgerEvent("account.event", event.toString());
    }

    /**
     * Process loan events to update the read model.
     */
    @KafkaListener(topics = "loan.events", groupId = "ledger-query-service")
    public void processLoanEvent(Object event) {
        logger.debug("Received loan event for read model update: {}", event);
        // In a full implementation, we would update the read model here
        ledgerQueryService.processLedgerEvent("loan.event", event.toString());
    }

    /**
     * Process payment events to update the read model.
     */
    @KafkaListener(topics = "payment.events", groupId = "ledger-query-service")
    public void processPaymentEvent(Object event) {
        logger.debug("Received payment event for read model update: {}", event);
        // In a full implementation, we would update the read model here
        ledgerQueryService.processLedgerEvent("payment.event", event.toString());
    }

    /**
     * Process trust score events to update the read model.
     */
    @KafkaListener(topics = "trust-score.events", groupId = "ledger-query-service")
    public void processTrustScoreEvent(Object event) {
        logger.debug("Received trust score event for read model update: {}", event);
        // In a full implementation, we would update the read model here
        ledgerQueryService.processLedgerEvent("trust-score.event", event.toString());
    }
}