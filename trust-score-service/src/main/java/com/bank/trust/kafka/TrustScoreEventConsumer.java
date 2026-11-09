package com.bank.trust.kafka;

import com.bank.trust.service.TrustScoreService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the events that move trust scores. Both listeners share the
 * {@code trust-score-service} group; the repository dedupes by Kafka eventId.
 */
@Component
public class TrustScoreEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TrustScoreEventConsumer.class);

    private final TrustScoreService trustScoreService;

    public TrustScoreEventConsumer(TrustScoreService trustScoreService) {
        this.trustScoreService = trustScoreService;
    }

    @KafkaListener(topics = "account.events", groupId = "trust-score-service")
    public void onAccountEvent(ConsumerRecord<String, String> record) {
        log.debug("account.events partition={} offset={}",
                record.partition(), record.offset());
        trustScoreService.apply("account.events", record.value());
    }

    @KafkaListener(topics = "loan.events", groupId = "trust-score-service")
    public void onLoanEvent(ConsumerRecord<String, String> record) {
        log.debug("loan.events partition={} offset={}",
                record.partition(), record.offset());
        trustScoreService.apply("loan.events", record.value());
    }
}
