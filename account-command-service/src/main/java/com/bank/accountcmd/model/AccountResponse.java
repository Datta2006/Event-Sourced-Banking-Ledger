package com.bank.accountcmd.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response object for account operations.
 */
public class AccountResponse {
    private UUID accountId;
    private String status;
    private OffsetDateTime openedAt;
    private BigDecimal availableBalance; // This would come from a read model in reality

    public AccountResponse() {}

    public AccountResponse(UUID accountId, String status, OffsetDateTime openedAt, BigDecimal availableBalance) {
        this.accountId = accountId;
        this.status = status;
        this.openedAt = openedAt;
        this.availableBalance = availableBalance;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(OffsetDateTime openedAt) {
        this.openedAt = openedAt;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        this.availableBalance = availableBalance;
    }
}