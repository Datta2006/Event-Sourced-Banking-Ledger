package com.bank.payment.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Command to reserve funds for a payment (online or offline).
 */
public class FundsReservedCommand {
    private UUID accountId;
    private BigDecimal amount;
    private OffsetDateTime expiresAt;
    private String idempotencyKey;

    public FundsReservedCommand() {}

    public FundsReservedCommand(UUID accountId, BigDecimal amount, OffsetDateTime expiresAt, String idempotencyKey) {
        this.accountId = accountId;
        this.amount = amount;
        this.expiresAt = expiresAt;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}