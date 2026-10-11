package com.bank.accountcmd.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Idempotency key store — docs/DATA_MODEL.md §3, v2 §6. Per-service copy
 * (schemas are not shared across services). Expired rows purge after 24h.
 */
@Entity
@Table(name = "processed_requests")
public class ProcessedRequest {

    @Id
    @Column(name = "key", nullable = false, updatable = false, length = 64)
    private String key;

    /** JSONB snapshot of the original response, replayed on retry (v2 §6). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_snapshot", nullable = false, columnDefinition = "jsonb")
    private String responseSnapshot;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    protected ProcessedRequest() {
    }

    public ProcessedRequest(String key, String responseSnapshot, OffsetDateTime expiresAt) {
        this.key = key;
        this.responseSnapshot = responseSnapshot;
        this.expiresAt = expiresAt;
    }

    public String getKey() {
        return key;
    }

    public String getResponseSnapshot() {
        return responseSnapshot;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }
}
