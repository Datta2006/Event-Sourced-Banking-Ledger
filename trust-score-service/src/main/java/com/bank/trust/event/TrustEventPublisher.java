package com.bank.trust.event;

import com.bank.trust.model.TrustViews.TrustScoreChangeView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes {@code TrustScoreChanged} to {@code trust-score.events}, keyed by
 * accountId. Loan Service consumes it into its local read-only tier cache.
 */
@Component
public class TrustEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TrustEventPublisher.class);
    public static final String TOPIC = "trust-score.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public TrustEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                               ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(TrustScoreChangeView change) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("accountId", change.accountId());
            payload.put("previousScore", change.scoreBefore());
            payload.put("newScore", change.scoreAfter());
            payload.put("previousTier", change.tierBefore());
            payload.put("newTier", change.tierAfter());
            payload.put("scoreBefore", change.scoreBefore());
            payload.put("scoreAfter", change.scoreAfter());
            payload.put("tierBefore", change.tierBefore());
            payload.put("tierAfter", change.tierAfter());
            payload.put("cause", change.reason());

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventId", change.eventId());
            envelope.put("aggregateId", change.accountId().toString());
            envelope.put("version", 0);
            envelope.put("type", "TrustScoreChanged");
            envelope.put("eventType", "TrustScoreChanged");
            envelope.put("payload", payload);
            envelope.put("correlationId", change.eventId().toString());
            envelope.put("occurredAt", change.changedAt().toString());
            kafkaTemplate.send(TOPIC, change.accountId().toString(),
                    objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            log.warn("Could not publish TrustScoreChanged for {}: {}",
                    change.accountId(), e.getMessage());
        }
    }

    /** Visible for tests that need the envelope shape without a broker. */
    public Map<String, Object> envelope(TrustScoreChangeView change) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", change.eventId());
        envelope.put("aggregateId", change.accountId().toString());
        envelope.put("version", 0);
        envelope.put("eventType", "TrustScoreChanged");
        envelope.put("payload", Map.of(
                "accountId", change.accountId(),
                "previousScore", change.scoreBefore(),
                "newScore", change.scoreAfter(),
                "previousTier", change.tierBefore(),
                "newTier", change.tierAfter(),
                "cause", change.reason()));
        envelope.put("occurredAt", OffsetDateTime.now().toString());
        envelope.put("correlationId", UUID.randomUUID().toString());
        return envelope;
    }
}
