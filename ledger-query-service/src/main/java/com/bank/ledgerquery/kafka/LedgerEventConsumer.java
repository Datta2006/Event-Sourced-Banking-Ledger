package com.bank.ledgerquery.kafka;

import com.bank.ledgerquery.service.EventProjector;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Projects account, loan, payment and trust events into the read model.
 *
 * <p>Every project is idempotent: events are keyed by {@code eventId} and account
 * mutations carry a version guard. This makes at-least-once delivery safe.</p>
 */
@Component
public class LedgerEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(LedgerEventConsumer.class);

    private final EventProjector projector;

    public LedgerEventConsumer(EventProjector projector) {
        this.projector = projector;
    }

    @KafkaListener(
            topics = {"account.events", "loan.events", "payment.events", "trust-score.events"},
            groupId = "${spring.kafka.consumer.group-id:ledger-query-service}")
    public void onEvent(ConsumerRecord<String, String> record) {
        log.debug("Projecting {} partition={} offset={}",
                record.topic(), record.partition(), record.offset());
        projector.applyKafka(record.topic(), record.value());
    }
}
