package com.bank.accountcmd.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command to open a new account.
 * The registration IP is resolved to a region (v2 §7.1) which becomes the
 * account's shard key for horizontal fragmentation.
 *
 * <p>{@code accountId} is optional. Production clients let the service generate
 * one; demo/seed scripts may pin deterministic ids so the proof scripts can
 * address accounts before they are opened.</p>
 */
public class OpenAccountCommand {
    private UUID accountId;
    private String holderName;
    private String registrationIp;
    private BigDecimal initialDeposit;
    private String idempotencyKey;

    public OpenAccountCommand() {}

    public OpenAccountCommand(UUID accountId, String holderName, String registrationIp,
                              BigDecimal initialDeposit, String idempotencyKey) {
        this.accountId = accountId;
        this.holderName = holderName;
        this.registrationIp = registrationIp;
        this.initialDeposit = initialDeposit;
        this.idempotencyKey = idempotencyKey;
    }

    public OpenAccountCommand(String holderName, String registrationIp, BigDecimal initialDeposit, String idempotencyKey) {
        this(null, holderName, registrationIp, initialDeposit, idempotencyKey);
    }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }

    public String getRegistrationIp() { return registrationIp; }
    public void setRegistrationIp(String registrationIp) { this.registrationIp = registrationIp; }

    public BigDecimal getInitialDeposit() { return initialDeposit; }
    public void setInitialDeposit(BigDecimal initialDeposit) { this.initialDeposit = initialDeposit; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}