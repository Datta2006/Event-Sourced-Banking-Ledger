package com.bank.payment.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Command to capture reserved funds (complete an offline payment).
 */
public class CaptureFundsCommand {
    private UUID reservationId;
    private UUID accountId;
    private String idempotencyKey;

    public CaptureFundsCommand() {}

    public CaptureFundsCommand(UUID reservationId, UUID accountId, String idempotencyKey) {
        this.reservationId = reservationId;
        this.accountId = accountId;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public void setReservationId(UUID reservationId) {
        this.reservationId = reservationId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}