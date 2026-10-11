package com.bank.accountcmd.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Event-store row — docs/DATA_MODEL.md §4. Append-only; never updated.
 * UNIQUE(aggregate_id, version) (V1__ledger_events.sql) is the ultimate
 * optimistic-concurrency guard (v2 §6).
 */
@Entity
@Table(name = "ledger_events")
public class LedgerEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "version", nullable = false, updatable = false)
    private int version;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    /** JSONB event body (docs/DATA_MODEL.md §4). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    /** Horizontal shard key for the deferred ShardingSphere retrofit (v2 §7.1). */
    @Column(name = "region", updatable = false)
    private String region;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    protected LedgerEvent() {
    }

    public LedgerEvent(UUID eventId, UUID aggregateId, int version, String eventType,
                       String payload, String region, OffsetDateTime occurredAt) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.version = version;
        this.eventType = eventType;
        this.payload = payload;
        this.region = region;
        this.occurredAt = occurredAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public int getVersion() {
        return version;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public String getRegion() {
        return region;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}
