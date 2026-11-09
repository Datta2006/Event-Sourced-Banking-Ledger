package com.bank.ledgerquery.readmodel;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Read-model projection of an account (docs/DATA_MODEL.md §6, v2 §2).
 *
 * <p>{@code lastVersion} makes the projection idempotent: a replayed or
 * redelivered event older than or equal to the projected version is ignored, so
 * balances are never double-applied.</p>
 */
@Document(collection = "accounts")
public class AccountReadModel {

    @Id
    private String accountId;
    private String holderName;
    private String status = "ACTIVE";
    @Indexed
    private String region;
    private String kycStatus = "PENDING";
    private BigDecimal availableBalance = BigDecimal.ZERO;
    private BigDecimal reservedBalance = BigDecimal.ZERO;
    private int lastVersion = -1;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public AccountReadModel() {
    }

    public AccountReadModel(String accountId, String holderName, String status, String region,
                            BigDecimal availableBalance, BigDecimal reservedBalance,
                            OffsetDateTime updatedAt) {
        this.accountId = accountId;
        this.holderName = holderName;
        this.status = status;
        this.region = region;
        this.availableBalance = availableBalance;
        this.reservedBalance = reservedBalance;
        this.updatedAt = updatedAt;
    }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getKycStatus() { return kycStatus; }
    public void setKycStatus(String kycStatus) { this.kycStatus = kycStatus; }

    public BigDecimal getAvailableBalance() { return availableBalance; }
    public void setAvailableBalance(BigDecimal availableBalance) { this.availableBalance = availableBalance; }

    public BigDecimal getReservedBalance() { return reservedBalance; }
    public void setReservedBalance(BigDecimal reservedBalance) { this.reservedBalance = reservedBalance; }

    public int getLastVersion() { return lastVersion; }
    public void setLastVersion(int lastVersion) { this.lastVersion = lastVersion; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
