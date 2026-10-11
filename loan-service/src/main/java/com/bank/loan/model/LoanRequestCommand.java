package com.bank.loan.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Command to request a loan.
 */
public class LoanRequestCommand {
    private UUID accountId;
    private BigDecimal amount;
    private String idempotencyKey;

    public LoanRequestCommand() {}

    public LoanRequestCommand(UUID accountId, BigDecimal amount, String idempotencyKey) {
        this.accountId = accountId;
        this.amount = amount;
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

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}