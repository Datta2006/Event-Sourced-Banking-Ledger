package com.bank.fraud.event;

import com.bank.fraud.model.FraudCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Publishes fraud case events to {@code fraud.events}, keyed by accountId. */
@Component
public class FraudEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(FraudEventPublisher.class);
    public static final String TOPIC = "fraud.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public FraudEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                               ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void caseOpened(FraudCase c) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseId", c.caseId());
        payload.put("accountId", c.accountId());
        payload.put("rule", c.rule());
        payload.put("riskScore", c.riskScore());
        payload.put("triggeredByEvent", c.triggeredByEvent());
        payload.put("status", c.status());
        payload.put("flaggedAt", c.flaggedAt());
        publish(c.accountId(), "FraudCaseOpened", payload);
    }

    public void caseReviewed(FraudCase c, String note) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseId", c.caseId());
        payload.put("accountId", c.accountId());
        payload.put("status", c.status());
        payload.put("note", note);
        payload.put("reviewedAt", c.reviewedAt());
        publish(c.accountId(), "FraudCaseReviewed", payload);
    }

    private void publish(UUID accountId, String type, Map<String, Object> payload) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventId", UUID.randomUUID().toString());
            envelope.put("aggregateId", accountId.toString());
            envelope.put("version", 0);
            envelope.put("type", type);
            envelope.put("eventType", type);
            envelope.put("payload", payload);
            envelope.put("correlationId", UUID.randomUUID().toString());
            envelope.put("occurredAt", OffsetDateTime.now().toString());
            kafkaTemplate.send(TOPIC, accountId.toString(),
                    objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            log.warn("Could not publish {} for {}: {}", type, accountId, e.getMessage());
        }
    }
}
