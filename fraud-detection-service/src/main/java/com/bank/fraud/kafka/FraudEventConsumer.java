package com.bank.fraud.kafka;

import com.bank.fraud.service.FraudDetectionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Listens to the three business topics and evaluates fraud rules. */
@Component
public class FraudEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(FraudEventConsumer.class);

    private final FraudDetectionService fraudDetection;

    public FraudEventConsumer(FraudDetectionService fraudDetection) {
        this.fraudDetection = fraudDetection;
    }

    @KafkaListener(topics = "account.events", groupId = "fraud-detection-service")
    public void onAccountEvent(ConsumerRecord<String, String> record) {
        evaluate("account.events", record);
    }

    @KafkaListener(topics = "payment.events", groupId = "fraud-detection-service")
    public void onPaymentEvent(ConsumerRecord<String, String> record) {
        evaluate("payment.events", record);
    }

    @KafkaListener(topics = "loan.events", groupId = "fraud-detection-service")
    public void onLoanEvent(ConsumerRecord<String, String> record) {
        evaluate("loan.events", record);
    }

    private void evaluate(String topic, ConsumerRecord<String, String> record) {
        log.debug("{} partition={} offset={}", topic, record.partition(), record.offset());
        try {
            fraudDetection.analyze(topic, record.value());
        } catch (RuntimeException e) {
            log.warn("Fraud evaluation failed for {} offset {}: {}",
                    topic, record.offset(), e.getMessage());
        }
    }
}
