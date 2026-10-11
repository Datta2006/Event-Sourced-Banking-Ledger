package com.bank.ledgerquery.readmodel;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Read-model projection — docs/DATA_MODEL.md §6 (collections: accounts, transactions).
 * The doc deliberately leaves the read model schemaless; these fields are the
 * minimal projection surface (available/reserved balance are the derived fields
 * v2 §4.2 step 1 references). Extended when the projection logic lands.
 */
@Document(collection = "accounts")
public class AccountReadModel {

    @Id
    private String accountId;

    private String holderName;

    private String status;

    private String region;

    private java.math.BigDecimal availableBalance;

    private java.math.BigDecimal reservedBalance;

    protected AccountReadModel() {
    }

    public AccountReadModel(String accountId, String holderName, String status, String region,
                            java.math.BigDecimal availableBalance, java.math.BigDecimal reservedBalance) {
        this.accountId = accountId;
        this.holderName = holderName;
        this.status = status;
        this.region = region;
        this.availableBalance = availableBalance;
        this.reservedBalance = reservedBalance;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getHolderName() {
        return holderName;
    }

    public String getStatus() {
        return status;
    }

    public String getRegion() {
        return region;
    }

    public java.math.BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public java.math.BigDecimal getReservedBalance() {
        return reservedBalance;
    }
}
