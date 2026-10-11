package com.bank.accountcmd.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command to withdraw money from an account.
 */
public class WithdrawCommand {
    private UUID accountId;
    private BigDecimal amount;
    private String idempotencyKey;

    public WithdrawCommand() {}

    public WithdrawCommand(UUID accountId, BigDecimal amount, String idempotencyKey) {
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