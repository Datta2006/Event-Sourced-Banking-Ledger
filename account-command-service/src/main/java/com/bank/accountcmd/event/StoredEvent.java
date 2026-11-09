package com.bank.accountcmd.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A row of the append-only, region-sharded event store. */
public record StoredEvent(
        UUID eventId,
        UUID aggregateId,
        int version,
        String eventType,
        String payloadJson,
        String region,
        String correlationId,
        String prevHash,
        String hash,
        OffsetDateTime occurredAt) {
}
