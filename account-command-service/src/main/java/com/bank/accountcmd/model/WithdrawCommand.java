package com.bank.accountcmd.model;

import java.math.BigDecimal;
import java.util.UUID;

/** Command to withdraw money from an account. */
public class WithdrawCommand {
    private UUID accountId;
    private BigDecimal amount;
    private String deviceId;
    private String idempotencyKey;

    public WithdrawCommand() {}

    public WithdrawCommand(UUID accountId, BigDecimal amount, String deviceId, String idempotencyKey) {
        this.accountId = accountId;
        this.amount = amount;
        this.deviceId = deviceId;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
