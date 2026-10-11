package com.bank.accountcmd.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Vertical-fragmentation "hot" fragment — docs/DATA_MODEL.md §4, v2 §7.2.
 * Small, hot, read on almost every request.
 */
@Entity
@Table(name = "account_core")
public class AccountCore {

    @Id
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "status")
    private String status;

    @Column(name = "region")
    private String region;

    @Column(name = "opened_at")
    private OffsetDateTime openedAt;

    protected AccountCore() {
    }

    public AccountCore(UUID accountId, String status, String region, OffsetDateTime openedAt) {
        this.accountId = accountId;
        this.status = status;
        this.region = region;
        this.openedAt = openedAt;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getStatus() {
        return status;
    }

    public String getRegion() {
        return region;
    }

    public OffsetDateTime getOpenedAt() {
        return openedAt;
    }
}
