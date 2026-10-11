package com.bank.fraud.kafka;

import com.bank.fraud.service.FraudDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kafka consumer for processing events to check for fraud.
 * Listens to account.events, loan.events, and payment.events to analyze
 * transactions for potential fraudulent activity.
 */
@Component
public class FraudEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(FraudEventConsumer.class);
    private static final Pattern TYPE = Pattern.compile("\"type\"\\s*:\\s*\"([A-Za-z]+)\"");

    private final FraudDetectionService fraudDetectionService;

    public FraudEventConsumer(FraudDetectionService fraudDetectionService) {
        this.fraudDetectionService = fraudDetectionService;
    }

    /**
     * Process account events for fraud detection.
     * The command service publishes {type, aggregateId, amount, occurredAt, ...}.
     */
    @KafkaListener(topics = "account.events", groupId = "fraud-detection-service")
    public void processAccountEvent(String event) {
        if (event == null || event.isBlank()) return;
        logger.debug("Received account event for fraud checking: {}", event);
        Matcher m = TYPE.matcher(event);
        String type = m.find() ? m.group(1) : null;
        boolean isFraud = fraudDetectionService.analyzeEventForFraud(type, event);
        if (isFraud) {
            logger.warn("Fraud case opened from account event: {}", event);
        }
    }

    /**
     * Process loan events for fraud detection.
     */
    @KafkaListener(topics = "loan.events", groupId = "fraud-detection-service")
    public void processLoanEvent(String event) {
        if (event == null || event.isBlank()) return;
        logger.debug("Received loan event for fraud checking: {}", event);
        Matcher m = TYPE.matcher(event);
        String type = m.find() ? m.group(1) : null;
        boolean isFraud = fraudDetectionService.analyzeEventForFraud(type, event);
        if (isFraud) {
            logger.warn("Fraud case opened from loan event: {}", event);
        }
    }

    /**
     * Process payment events for fraud detection.
     */
    @KafkaListener(topics = "payment.events", groupId = "fraud-detection-service")
    public void processPaymentEvent(String event) {
        if (event == null || event.isBlank()) return;
        logger.debug("Received payment event for fraud checking: {}", event);
        Matcher m = TYPE.matcher(event);
        String type = m.find() ? m.group(1) : null;
        boolean isFraud = fraudDetectionService.analyzeEventForFraud(type, event);
        if (isFraud) {
            logger.warn("Fraud case opened from payment event: {}", event);
        }
    }
}
