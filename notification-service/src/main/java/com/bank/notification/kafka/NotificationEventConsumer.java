package com.bank.notification.kafka;

import com.bank.notification.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for processing events to generate customer notifications.
 * Listens to account.events, loan.events, payment.events, and trust-score.events
 * to send notifications to customers about important account activities.
 */
@Component
public class NotificationEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NotificationEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "account.events", groupId = "notification-service")
    public void processAccountEvent(String eventJson) {
        read(eventJson).ifPresent(event -> {
            String type = text(event, "type");
            String accountId = text(event, "aggregateId");
            if (type != null && accountId != null &&
                    ("MoneyDeposited".equals(type) || "MoneyWithdrawn".equals(type))) {
                notificationService.sendNotification(accountId, "customer@example.com",
                        "Account Activity Notification",
                        "We detected a " + type + " on your account. Amount: " + text(event, "amount"),
                        type);
                logger.info("Notification sent for account event: {}", type);
            }
        });
    }

    @KafkaListener(topics = "loan.events", groupId = "notification-service")
    public void processLoanEvent(String eventJson) {
        read(eventJson).ifPresent(event -> {
            String type = text(event, "type");
            String accountId = text(event, "accountId");
            if (type != null && accountId != null) {
                notificationService.sendNotification(accountId, "customer@example.com",
                        "Loan Activity Notification",
                        "We detected a " + type + " on your loan account.",
                        type);
                logger.info("Notification sent for loan event: {}", type);
            }
        });
    }

    @KafkaListener(topics = "payment.events", groupId = "notification-service")
    public void processPaymentEvent(String eventJson) {
        read(eventJson).ifPresent(event -> {
            String type = text(event, "type");
            String accountId = text(event, "accountId");
            if (type != null && accountId != null &&
                    ("FundsCaptured".equals(type) || "FundsReleased".equals(type) || "FundsReserved".equals(type))) {
                notificationService.sendNotification(accountId, "customer@example.com",
                        "Payment Activity Notification",
                        "We detected a " + type + " on your payment account.",
                        type);
                logger.info("Notification sent for payment event: {}", type);
            }
        });
    }

    @KafkaListener(topics = "trust-score.events", groupId = "notification-service")
    public void processTrustScoreEvent(String eventJson) {
        read(eventJson).ifPresent(event -> {
            String accountId = text(event, "accountId");
            JsonNode newScore = event.get("newScore");
            String cause = text(event, "cause");
            if (accountId != null && newScore != null && newScore.isNumber()) {
                notificationService.sendNotification(accountId, "customer@example.com",
                        "Trust Score Update Notification",
                        "Your trust score has been updated to " + newScore.asInt()
                                + (cause == null ? "." : " due to " + cause + "."),
                        "TrustScoreChanged");
                logger.info("Notification sent for trust score change: {}", accountId);
            }
        });
    }

    /** Null-safe parse; tolerates foreign envelopes and non-JSON payloads. */
    private java.util.Optional<JsonNode> read(String eventJson) {
        if (eventJson == null || eventJson.isBlank()) return java.util.Optional.empty();
        try {
            return java.util.Optional.of(objectMapper.readTree(eventJson));
        } catch (Exception e) {
            logger.error("Error parsing event for notification: {}", e.getMessage());
            return java.util.Optional.empty();
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }
}
