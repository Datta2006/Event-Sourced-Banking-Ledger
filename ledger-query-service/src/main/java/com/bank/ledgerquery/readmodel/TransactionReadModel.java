package com.bank.ledgerquery.readmodel;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;

/**
 * Read-model projection — docs/DATA_MODEL.md §6 (collections: accounts, transactions).
 * Schemaless by design; minimal transaction-history surface for now.
 */
@Document(collection = "transactions")
public class TransactionReadModel {

    @Id
    private String transactionId;

    private String accountId;

    private String type;

    private java.math.BigDecimal amount;

    private String currency;

    private OffsetDateTime occurredAt;

    protected TransactionReadModel() {
    }

    public TransactionReadModel(String transactionId, String accountId, String type,
                                java.math.BigDecimal amount, String currency, OffsetDateTime occurredAt) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.occurredAt = occurredAt;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getType() {
        return type;
    }

    public java.math.BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}
