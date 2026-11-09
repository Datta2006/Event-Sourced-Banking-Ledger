package com.bank.ledgerquery.readmodel;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Read-model projection of one money movement (docs/DATA_MODEL.md §6).
 * {@code _id} is the event id plus a side discriminator, so replay is idempotent.
 */
@Document(collection = "transactions")
public class TransactionReadModel {

    @Id
    private String transactionId;
    @Indexed
    private String accountId;
    private String type;
    private BigDecimal amount;
    private String reference;
    private String eventId;
    @Indexed
    private OffsetDateTime occurredAt;

    public TransactionReadModel() {
    }

    public TransactionReadModel(String transactionId, String accountId, String type,
                                BigDecimal amount, OffsetDateTime occurredAt) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.occurredAt = occurredAt;
    }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
}
