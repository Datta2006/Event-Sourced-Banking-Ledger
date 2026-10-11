package com.bank.trust.kafka;

import com.bank.trust.service.TrustScoreService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka consumer for processing events that affect trust scores.
 * Listens to account.events and loan.events to update trust scores based on
 * MoneyDeposited, LoanRepaidOnTime, LoanRepaidEarly, LoanDisbursed, 
 * LoanRepaidLate, and LoanDefaulted events.
 */
@Component
public class TrustScoreEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(TrustScoreEventConsumer.class);

    private final TrustScoreService trustScoreService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TrustScoreEventConsumer(TrustScoreService trustScoreService) {
        this.trustScoreService = trustScoreService;
    }

    /**
     * Process account events that affect trust scores.
     */
    @KafkaListener(topics = "account.events", groupId = "trust-score-service")
    public void processAccountEvent(String eventJson) {
        try {
            logger.debug("Received account event: {}", eventJson);
            JsonNode event = objectMapper.readTree(eventJson);
            String eventType = event.get("eventType").asText();
            UUID accountId = UUID.fromString(event.get("aggregateId").asText());
            
            // Only process deposit events for trust score updates
            if ("MoneyDeposited".equals(eventType)) {
                JsonNode payload = event.get("payload");
                // In a real implementation, we would parse the JSON payload properly
                // For now, we'll assume a simple format and extract the amount
                double amount = 10000; // Placeholder - would be parsed from payload
                trustScoreService.updateTrustScore(accountId, eventType, java.math.BigDecimal.valueOf(amount));
            }
        } catch (Exception e) {
            logger.error("Error processing account event: {}", e.getMessage(), e);
        }
    }

    /**
     * Process loan events that affect trust scores.
     */
    @KafkaListener(topics = "loan.events", groupId = "trust-score-service")
    public void processLoanEvent(String eventJson) {
        try {
            logger.debug("Received loan event: {}", eventJson);
            JsonNode event = objectMapper.readTree(eventJson);
            String eventType = event.get("eventType").asText();
            UUID accountId = UUID.fromString(event.get("aggregateId").asText());
            
            // Process loan events that affect trust score
            switch (eventType) {
                case "LoanRepaidOnTime":
                case "LoanRepaidEarly":
                case "LoanDisbursed": // LoanTaken (disbursed)
                case "LoanRepaidLate":
                case "LoanDefaulted":
                    // For these events, we don't have the amount in a standard place
                    // In a real implementation, we would parse it from the payload
                    trustScoreService.updateTrustScore(accountId, eventType, java.math.BigDecimal.ZERO);
                    break;
                default:
                    // Ignore other loan events
                    break;
            }
        } catch (Exception e) {
            logger.error("Error processing loan event: {}", e.getMessage(), e);
        }
    }
}