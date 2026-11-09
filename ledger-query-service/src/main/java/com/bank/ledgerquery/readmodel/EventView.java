package com.bank.ledgerquery.readmodel;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Projected domain event for the dashboard feed (docs/api_spec/ledger-query-service.yaml).
 * {@code _id} is the event id, so Kafka redelivery and replay are idempotent.
 */
@Document(collection = "events")
public class EventView {

    @Id
    private String eventId;
    @Indexed
    private String aggregateId;
    private int version;
    @Indexed
    private String type;
    private Map<String, Object> payload = new LinkedHashMap<>();
    @Indexed
    private OffsetDateTime occurredAt;
    private String region;
    private String correlationId;

    public EventView() {}

    public EventView(String eventId, String aggregateId, int version, String type,
                     Map<String, Object> payload, OffsetDateTime occurredAt, String region) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.version = version;
        this.type = type;
        this.payload = payload != null ? payload : new LinkedHashMap<>();
        this.occurredAt = occurredAt;
        this.region = region;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload != null ? payload : new LinkedHashMap<>(); }

    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
}
