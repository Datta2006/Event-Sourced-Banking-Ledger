package com.bank.loan.kafka;

import com.bank.loan.service.TrustTierService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Maintains the local trust-tier cache from {@code trust-score.events}.
 * Loan requests then read the cache synchronously, never the trust service (v2 §3.6).
 */
@Component
public class LoanEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(LoanEventConsumer.class);

    private final TrustTierService trustTierService;

    public LoanEventConsumer(TrustTierService trustTierService) {
        this.trustTierService = trustTierService;
    }

    @KafkaListener(topics = "trust-score.events", groupId = "loan-service")
    public void onTrustEvent(ConsumerRecord<String, String> record) {
        log.debug("Trust event partition={} offset={}", record.partition(), record.offset());
        trustTierService.apply(record.value());
    }
}
