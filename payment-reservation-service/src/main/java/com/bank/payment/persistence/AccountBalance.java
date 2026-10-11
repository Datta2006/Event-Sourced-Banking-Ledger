package com.bank.payment.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Own-schema balance projection for the payment service (schema payment,
 * table account_balances). Maintained from account events consumed off
 * Kafka plus this service's own reserved/captured/released transitions —
 * the service stays the owner of its reservation logic without joining
 * across schemas at runtime (ADR-002, one schema per data-owning service).
 */
@Entity
@Table(name = "account_balances")
public class AccountBalance {

    /** Account id as string — the projection's key in the payment schema. */
    @Id
    @Column(name = "account_id", nullable = false, updatable = false, length = 36)
    private String accountId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "available_balance", nullable = false)
    private BigDecimal availableBalance;

    @Column(name = "reserved_balance", nullable = false)
    private BigDecimal reservedBalance;

    /** Optimistic-concurrency version, bumped on every mutation. */
    @Column(name = "version", nullable = false)
    private long version;

    /** Faithful to the migration: no no-arg constructor needed by JPA here... */
    protected AccountBalance() {
    }

    public AccountBalance(String accountId, String status,
                          BigDecimal availableBalance, BigDecimal reservedBalance) {
        this.accountId = accountId;
        this.status = status;
        this.availableBalance = availableBalance;
        this.reservedBalance = reservedBalance;
        this.version = 0;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.version++;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        this.availableBalance = availableBalance;
        this.version++;
    }

    public BigDecimal getReservedBalance() {
        return reservedBalance;
    }

    public void setReservedBalance(BigDecimal reservedBalance) {
        this.reservedBalance = reservedBalance;
        this.version++;
    }

    public long getVersion() {
        return version;
    }
}
