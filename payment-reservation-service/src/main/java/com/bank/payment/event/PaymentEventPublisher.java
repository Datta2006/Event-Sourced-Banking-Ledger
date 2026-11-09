package com.bank.payment.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Publishes Funds* events to {@code payment.events}, keyed by accountId. */
@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);
    public static final String TOPIC = "payment.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PaymentEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(UUID accountId, String type, Map<String, Object> payload, String correlationId) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventId", UUID.randomUUID().toString());
            envelope.put("aggregateId", accountId.toString());
            envelope.put("version", 0);
            envelope.put("type", type);
            envelope.put("eventType", type);
            envelope.put("payload", payload);
            envelope.put("correlationId", correlationId);
            envelope.put("occurredAt", OffsetDateTime.now().toString());
            kafkaTemplate.send(TOPIC, accountId.toString(), objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            log.warn("Could not publish {} for {}: {}", type, accountId, e.getMessage());
        }
    }
}
