package com.bank.ledgerquery.service;

import com.bank.ledgerquery.config.ShardedLedger;
import com.bank.ledgerquery.readmodel.AccountReadModel;
import com.bank.ledgerquery.readmodel.EventView;
import com.bank.ledgerquery.readmodel.TransactionReadModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Idempotent projection from domain events into the read model.
 *
 * <p>Account balances are mutated only by {@code account.events}. Loan and payment
 * services record their business events and then drive the authoritative balance
 * change through the account service's internal endpoints, so projecting their
 * events into balances as well would double-count. Their events still appear in
 * the dashboard event feed.</p>
 */
@Component
public class EventProjector {

    private static final Logger log = LoggerFactory.getLogger(EventProjector.class);
    public static final String ACCOUNT_TOPIC = "account.events";

    private final MongoTemplate mongo;
    private final ObjectMapper objectMapper;

    public EventProjector(MongoTemplate mongo, ObjectMapper objectMapper) {
        this.mongo = mongo;
        this.objectMapper = objectMapper;
    }

    /** Apply one Kafka envelope (JSON string). */
    public void applyKafka(String topic, String envelopeJson) {
        if (envelopeJson == null || envelopeJson.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(envelopeJson);
            EventView event = toEvent(root);
            if (event == null) {
                log.warn("Skipping malformed event on {}: {}", topic, envelopeJson);
                return;
            }
            saveEvent(event);
            if (ACCOUNT_TOPIC.equals(topic) || topic == null) {
                applyToAccount(event, root.path("payload"));
            }
        } catch (Exception e) {
            // Throwing would make the listener retry and stall the partition; log
            // loudly and let the periodic replay repair the projection instead.
            log.error("Could not project event from {}: {}", topic, e.getMessage(), e);
        }
    }

    /** Apply one event read directly from a regional shard (replay path). */
    public void applyStored(ShardedLedger.StoredLedgerEvent stored) {
        try {
            JsonNode payload = objectMapper.readTree(stored.payloadJson());
            EventView event = new EventView(
                    stored.eventId().toString(),
                    stored.aggregateId().toString(),
                    stored.version(),
                    stored.eventType(),
                    toMap(payload),
                    stored.occurredAt(),
                    stored.region());
            event.setCorrelationId(stored.correlationId());
            saveEvent(event);
            applyToAccount(event, payload);
        } catch (Exception e) {
            log.error("Replay could not project {} v{}: {}",
                    stored.aggregateId(), stored.version(), e.getMessage(), e);
        }
    }

    public void resetReadModel() {
        mongo.dropCollection(AccountReadModel.class);
        mongo.dropCollection(TransactionReadModel.class);
        mongo.dropCollection(EventView.class);
    }

    private EventView toEvent(JsonNode root) {
        String eventId = text(root, "eventId");
        String aggregateId = text(root, "aggregateId");
        JsonNode payload = root.path("payload");
        if (aggregateId == null || aggregateId.isBlank()) {
            aggregateId = firstText(payload, "accountId", "aggregateId", "loanId", "reservationId");
        }
        if (eventId == null || eventId.isBlank() || aggregateId == null || aggregateId.isBlank()) {
            return null;
        }
        String type = text(root, "type");
        if (type == null) {
            type = text(root, "eventType");
        }
        String occurredAt = text(root, "occurredAt");
        EventView event = new EventView();
        event.setEventId(eventId);
        event.setAggregateId(aggregateId);
        event.setVersion(root.path("version").asInt(0));
        event.setType(type == null ? "Unknown" : type);
        event.setPayload(toMap(payload));
        event.setOccurredAt(occurredAt == null ? OffsetDateTime.now() : OffsetDateTime.parse(occurredAt));
        event.setRegion(text(root, "region"));
        event.setCorrelationId(text(root, "correlationId"));
        return event;
    }

    private void saveEvent(EventView event) {
        try {
            mongo.insert(event);
        } catch (DuplicateKeyException duplicate) {
            // Kafka at-least-once / replay duplicate: the projection below is still
            // applied because it is guarded by the aggregate version.
            log.debug("Duplicate event {} ignored for storage", event.getEventId());
        }
    }

    private void applyToAccount(EventView event, JsonNode payload) {
        UUID accountId = uuid(event.getAggregateId());
        if (accountId == null) {
            return;
        }
        String accountIdString = accountId.toString();
        AccountReadModel account = mongo.findById(accountIdString, AccountReadModel.class);
        boolean fresh = account == null;
        if (fresh) {
            account = new AccountReadModel();
            account.setAccountId(accountIdString);
            account.setCreatedAt(event.getOccurredAt());
        }
        if (event.getVersion() <= account.getLastVersion()) {
            return;
        }

        BigDecimal amount = decimal(payload, "amount");
        String reference = text(payload, "reference");
        switch (event.getType()) {
            case "AccountOpened" -> {
                account.setHolderName(text(payload, "holderName"));
                account.setRegion(text(payload, "region"));
                account.setStatus("ACTIVE");
                String kyc = text(payload, "kycStatus");
                account.setKycStatus(kyc == null ? "PENDING" : kyc);
            }
            case "AccountClosed" -> account.setStatus("CLOSED");
            case "AccountKycUpdated" -> {
                String kyc = text(payload, "kycStatus");
                if (kyc != null) {
                    account.setKycStatus(kyc);
                }
            }
            case "MoneyDeposited" -> account.setAvailableBalance(
                    nz(account.getAvailableBalance()).add(amount));
            case "MoneyWithdrawn" -> account.setAvailableBalance(
                    nz(account.getAvailableBalance()).subtract(amount));
            case "FundsReserved" -> {
                account.setAvailableBalance(nz(account.getAvailableBalance()).subtract(amount));
                account.setReservedBalance(nz(account.getReservedBalance()).add(amount));
            }
            case "FundsCaptured" -> account.setReservedBalance(
                    nz(account.getReservedBalance()).subtract(amount));
            case "FundsReleased" -> {
                account.setReservedBalance(nz(account.getReservedBalance()).subtract(amount));
                account.setAvailableBalance(nz(account.getAvailableBalance()).add(amount));
            }
            case "TransferSourceReimbursed" -> account.setAvailableBalance(
                    nz(account.getAvailableBalance()).add(amount));
            default -> {
                // Transfer markers and loan events do not change signed balance.
            }
        }
        account.setLastVersion(event.getVersion());
        account.setUpdatedAt(event.getOccurredAt());
        if (account.getRegion() == null) {
            account.setRegion(event.getRegion());
        }
        mongo.save(account);

        String txType = transactionType(event.getType(), reference);
        if (txType != null) {
            TransactionReadModel tx = new TransactionReadModel();
            tx.setTransactionId(event.getEventId());
            tx.setAccountId(accountIdString);
            tx.setType(txType);
            tx.setAmount(amount);
            tx.setReference(reference);
            tx.setEventId(event.getEventId());
            tx.setOccurredAt(event.getOccurredAt());
            try {
                mongo.insert(tx);
            } catch (DuplicateKeyException duplicate) {
                // same event replayed: transaction already exists
            }
        }
        log.debug("Projected {} v{} for {}", event.getType(), event.getVersion(), accountId);
    }

    private static String transactionType(String eventType, String reference) {
        return switch (eventType) {
            case "MoneyDeposited" -> {
                if ("TRANSFER_IN".equals(reference)) {
                    yield "TRANSFER_IN";
                }
                if ("LOAN_DISBURSEMENT".equals(reference)) {
                    yield "LOAN_DISBURSEMENT";
                }
                yield "DEPOSIT";
            }
            case "MoneyWithdrawn" -> {
                if ("TRANSFER_OUT".equals(reference)) {
                    yield "TRANSFER_OUT";
                }
                if (reference != null && reference.startsWith("LOAN_REPAYMENT")) {
                    yield "LOAN_REPAYMENT";
                }
                yield "WITHDRAWAL";
            }
            case "FundsReserved" -> "RESERVATION";
            case "FundsCaptured" -> "CAPTURE";
            case "FundsReleased" -> "RELEASE";
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(JsonNode payload) {
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            return new LinkedHashMap<>();
        }
        return objectMapper.convertValue(payload, Map.class);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static BigDecimal decimal(JsonNode payload, String field) {
        JsonNode node = payload == null ? null : payload.get(field);
        if (node == null || node.isNull()) {
            return BigDecimal.ZERO;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
