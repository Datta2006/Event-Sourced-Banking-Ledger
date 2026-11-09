package com.bank.ledgerquery.service;

import com.bank.ledgerquery.config.ShardedLedger;
import com.bank.ledgerquery.readmodel.AccountReadModel;
import com.bank.ledgerquery.readmodel.EventView;
import com.bank.ledgerquery.readmodel.TransactionReadModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-model queries, per-account event reads, and full replay/rebuild. */
@Service
public class LedgerQueryService {

    private static final Logger log = LoggerFactory.getLogger(LedgerQueryService.class);

    private final MongoTemplate mongo;
    private final ShardedLedger shardedLedger;
    private final EventProjector projector;
    private final ObjectMapper objectMapper;

    public LedgerQueryService(MongoTemplate mongo, ShardedLedger shardedLedger,
                              EventProjector projector, ObjectMapper objectMapper) {
        this.mongo = mongo;
        this.shardedLedger = shardedLedger;
        this.projector = projector;
        this.objectMapper = objectMapper;
    }

    public List<AccountReadModel> listAccounts() {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "updatedAt"));
        return mongo.find(query, AccountReadModel.class);
    }

    public AccountReadModel getAccount(String accountId) {
        AccountReadModel account = mongo.findById(accountId, AccountReadModel.class);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }
        return account;
    }

    public List<TransactionReadModel> listTransactions(String accountId, OffsetDateTime from,
                                                       OffsetDateTime to, int page, int size) {
        Query query = new Query();
        List<Criteria> criteria = new ArrayList<>();
        if (accountId != null && !accountId.isBlank()) {
            criteria.add(Criteria.where("accountId").is(accountId));
        }
        if (from != null) {
            criteria.add(Criteria.where("occurredAt").gte(from));
        }
        if (to != null) {
            criteria.add(Criteria.where("occurredAt").lte(to));
        }
        if (!criteria.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteria));
        }
        query.with(Sort.by(Sort.Direction.DESC, "occurredAt"));
        int safeSize = Math.min(Math.max(size, 1), 200);
        query.skip((long) Math.max(page, 0) * safeSize).limit(safeSize);
        return mongo.find(query, TransactionReadModel.class);
    }

    public List<EventView> listRecentEvents(OffsetDateTime since, int limit) {
        Query query = new Query();
        if (since != null) {
            query.addCriteria(Criteria.where("occurredAt").gt(since));
        }
        query.with(Sort.by(Sort.Direction.DESC, "occurredAt"));
        query.limit(Math.min(Math.max(limit, 1), 200));
        return mongo.find(query, EventView.class);
    }

    /** Per-account events come from the authoritative region-sharded store. */
    public List<EventView> listAccountEvents(String accountId) {
        UUID id;
        try {
            id = UUID.fromString(accountId);
        } catch (IllegalArgumentException e) {
            throw new AccountNotFoundException(accountId);
        }
        List<EventView> events = new ArrayList<>();
        for (ShardedLedger.StoredLedgerEvent stored : shardedLedger.accountEvents(id)) {
            EventView view = new EventView();
            view.setEventId(stored.eventId().toString());
            view.setAggregateId(stored.aggregateId().toString());
            view.setVersion(stored.version());
            view.setType(stored.eventType());
            view.setRegion(stored.region());
            view.setOccurredAt(stored.occurredAt());
            view.setCorrelationId(stored.correlationId());
            view.setPayload(readMap(stored.payloadJson()));
            events.add(view);
        }
        if (events.isEmpty() && mongo.findById(accountId, AccountReadModel.class) == null) {
            throw new AccountNotFoundException(accountId);
        }
        return events;
    }

    public int replayAll(boolean dropFirst) {
        if (dropFirst) {
            projector.resetReadModel();
        }
        List<ShardedLedger.StoredLedgerEvent> events = shardedLedger.allEvents();
        for (ShardedLedger.StoredLedgerEvent event : events) {
            projector.applyStored(event);
        }
        log.info("Replayed {} events into the read model", events.size());
        return events.size();
    }

    public long accountCount() {
        return mongo.count(new Query(), AccountReadModel.class);
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    public static class AccountNotFoundException extends RuntimeException {
        public AccountNotFoundException(String accountId) {
            super("Account not found: " + accountId);
        }
    }
}
