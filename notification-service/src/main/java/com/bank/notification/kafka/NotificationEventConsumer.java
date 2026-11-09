package com.bank.notification.kafka;

import com.bank.notification.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Receives every business topic that can produce a customer notification. */
@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final NotificationService notifications;

    public NotificationEventConsumer(NotificationService notifications) {
        this.notifications = notifications;
    }

    @KafkaListener(topics = "account.events", groupId = "notification-service")
    public void onAccountEvent(ConsumerRecord<String, String> record) {
        consume("account.events", record);
    }

    @KafkaListener(topics = "loan.events", groupId = "notification-service")
    public void onLoanEvent(ConsumerRecord<String, String> record) {
        consume("loan.events", record);
    }

    @KafkaListener(topics = "payment.events", groupId = "notification-service")
    public void onPaymentEvent(ConsumerRecord<String, String> record) {
        consume("payment.events", record);
    }

    @KafkaListener(topics = "trust-score.events", groupId = "notification-service")
    public void onTrustEvent(ConsumerRecord<String, String> record) {
        consume("trust-score.events", record);
    }

    @KafkaListener(topics = "fraud.events", groupId = "notification-service")
    public void onFraudEvent(ConsumerRecord<String, String> record) {
        consume("fraud.events", record);
    }

    private void consume(String topic, ConsumerRecord<String, String> record) {
        log.debug("{} partition={} offset={}", topic, record.partition(), record.offset());
        try {
            notifications.consume(topic, record.value());
        } catch (RuntimeException e) {
            log.warn("Notification failed for {} offset {}: {}",
                    topic, record.offset(), e.getMessage());
        }
    }
}
