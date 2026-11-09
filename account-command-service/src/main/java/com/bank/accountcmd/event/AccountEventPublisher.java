package com.bank.accountcmd.event;

import com.bank.accountcmd.domain.AccountState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Publishes domain events to {@code account.events}, keyed by accountId. */
@Component
public class AccountEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AccountEventPublisher.class);
    public static final String TOPIC = "account.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public AccountEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(StoredEvent event) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventId", event.eventId());
            envelope.put("aggregateId", event.aggregateId());
            envelope.put("version", event.version());
            envelope.put("type", event.eventType());
            envelope.put("eventType", event.eventType());
            envelope.put("payload", objectMapper.readTree(event.payloadJson()));
            envelope.put("region", event.region());
            envelope.put("correlationId", event.correlationId());
            envelope.put("occurredAt", event.occurredAt());
            envelope.put("prevHash", event.prevHash());
            envelope.put("hash", event.hash());
            kafkaTemplate.send(TOPIC, event.aggregateId().toString(),
                    objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            // The event store is authoritative; Kafka is the notification path. A broker
            // hiccup must not roll back a committed bank event.
            log.warn("Could not publish {} v{} for {}: {}",
                    event.eventType(), event.version(), event.aggregateId(), e.getMessage());
        }
    }
}
