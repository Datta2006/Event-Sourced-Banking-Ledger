package com.bank.payment.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Offline-payment reservation — docs/DATA_MODEL.md §2, v2 §4.3.
 * The partial unique index idx_reservation_capture is the DB-level double-spend
 * backstop (v2 §4.2 step 5); enforce capturedAt only via the CAPTURED transition.
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "amount", nullable = false)
    private java.math.BigDecimal amount;

    /** RESERVED, CAPTURED, RELEASED, EXPIRED (v2 §4.3). */
    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "captured_at")
    private OffsetDateTime capturedAt;

    protected Reservation() {
    }

    public Reservation(UUID reservationId, UUID accountId, java.math.BigDecimal amount,
                       String status, OffsetDateTime expiresAt) {
        this.reservationId = reservationId;
        this.accountId = accountId;
        this.amount = amount;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public java.math.BigDecimal getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getCapturedAt() {
        return capturedAt;
    }
}
