package com.bank.loan.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Loan request — docs/DATA_MODEL.md §1, v2 §3.2.
 * Status lifecycle: QUEUED, WAITING_FOR_FUNDS, APPROVED, DISBURSED, REJECTED.
 */
@Entity
@Table(name = "loan_requests")
public class LoanRequest {

    @Id
    @Column(name = "loan_id", nullable = false, updatable = false)
    private UUID loanId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "amount", nullable = false)
    private java.math.BigDecimal amount;

    /** 0=Low .. 3=Excellent, snapshotted at request time (v2 §3.2). */
    @Column(name = "trust_tier", nullable = false, updatable = false)
    private short trustTier;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "status", nullable = false)
    private String status;

    protected LoanRequest() {
    }

    public LoanRequest(UUID loanId, UUID accountId, java.math.BigDecimal amount,
                       short trustTier, OffsetDateTime requestedAt, String status) {
        this.loanId = loanId;
        this.accountId = accountId;
        this.amount = amount;
        this.trustTier = trustTier;
        this.requestedAt = requestedAt;
        this.status = status;
    }

    public UUID getLoanId() {
        return loanId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public java.math.BigDecimal getAmount() {
        return amount;
    }

    public short getTrustTier() {
        return trustTier;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public String getStatus() {
        return status;
    }
}
