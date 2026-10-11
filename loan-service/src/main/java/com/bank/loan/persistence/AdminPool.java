package com.bank.loan.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Admin loan pool — docs/DATA_MODEL.md §1, v2 §3.2.
 * `version` mirrors the event-store optimistic-concurrency pattern.
 * The Valkey mirror `admin_pool:balance` is what the atomic Lua script debits;
 * this row is the durable, doc-defined record of the pool.
 */
@Entity
@Table(name = "admin_pool")
public class AdminPool {

    @Id
    @Column(name = "pool_id", nullable = false, updatable = false)
    private UUID poolId;

    @Column(name = "available_balance", nullable = false)
    private java.math.BigDecimal availableBalance;

    @Column(name = "version", nullable = false)
    private int version;

    protected AdminPool() {
    }

    public AdminPool(UUID poolId, java.math.BigDecimal availableBalance, int version) {
        this.poolId = poolId;
        this.availableBalance = availableBalance;
        this.version = version;
    }

    public UUID getPoolId() {
        return poolId;
    }

    public java.math.BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public int getVersion() {
        return version;
    }
}
