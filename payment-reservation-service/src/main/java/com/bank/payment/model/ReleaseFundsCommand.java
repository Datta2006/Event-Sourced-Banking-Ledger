package com.bank.payment.model;

import java.util.UUID;

/**
 * Command to release reserved funds (expire or cancel a reservation).
 */
public class ReleaseFundsCommand {
    private UUID reservationId;
    private UUID accountId;
    private String reason; // EXPIRED or MERCHANT_DECLINED
    private String idempotencyKey;

    public ReleaseFundsCommand() {}

    public ReleaseFundsCommand(UUID reservationId, UUID accountId, String reason, String idempotencyKey) {
        this.reservationId = reservationId;
        this.accountId = accountId;
        this.reason = reason;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}