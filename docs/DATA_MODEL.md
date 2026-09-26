# Data Model — Per-Service Ownership

## Data Ownership

| Service | Database | Type | Rationale |
|---|---|---|---|
| **Account Command** | PostgreSQL (`ddbs`) | Relational | ACID required for event store writes and account balance updates |
| **Ledger Query** | MongoDB (`ddbs`) | Document | Denormalised read models fit document schema; flexible for query patterns |
| **Fraud Detection** | PostgreSQL | Relational | Fraud rules and alert state need ACID and relationships |
| **Notification** | PostgreSQL | Relational | Notification log and delivery status |
| **API Gateway** | None | — | Stateless routing layer |

---

## Event Store Schema (PostgreSQL — shared)

```sql
CREATE TABLE events (
    event_id       UUID PRIMARY KEY,
    aggregate_id   UUID NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB NOT NULL,
    metadata       JSONB,
    version        BIGINT NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_events_aggregate_version ON events(aggregate_id, version);
CREATE INDEX idx_events_aggregate ON events(aggregate_id, occurred_at);
CREATE INDEX idx_events_type ON events(event_type, occurred_at);
```

### Optimistic Concurrency
- Each write includes expected `version` = current max version + 1
- Conflict → command rejected with `409 CONFLICT`
- Guarantees exactly-once semantics per aggregate despite retries

---

## Ledger Query Read Models (MongoDB)

### `balances` collection
```javascript
{
  _id: "accountId",          // account UUID
  balance: 1000.00,
  currency: "USD",
  accountHolderName: "string",
  status: "OPEN",
  lastUpdated: "2026-09-26T00:00:00Z",
  eventVersion: 5
}
```

### `statements` collection
```javascript
{
  _id: ObjectId,
  accountId: "uuid",
  eventId: "uuid",
  eventType: "MoneyDeposited",
  amount: 100.00,
  balanceAfter: 100.00,
  counterpartyAccountId: "uuid | null",
  occurredAt: "2026-09-26T00:00:00Z",
  reference: "string"
}
```

### `transfers` collection
```javascript
{
  _id: ObjectId,
  transferId: "uuid",
  sourceAccountId: "uuid",
  destinationAccountId: "uuid",
  amount: 250.00,
  currency: "USD",
  status: "COMPLETED",
  sourceBalanceAfter: 750.00,
  destinationBalanceAfter: 1250.00,
  completedAt: "2026-09-26T00:00:00Z"
}
```

### Query-optimised indexes
```javascript
db.statements.createIndex({ accountId: 1, occurredAt: -1 })
db.statements.createIndex({ accountId: 1, eventType: 1, occurredAt: -1 })
db.transfers.createIndex({ sourceAccountId: 1, occurredAt: -1 })
db.transfers.createIndex({ destinationAccountId: 1, occurredAt: -1 })
db.balances.createIndex({ status: 1, balance: 1 })
```

---

## Consistency Model

| Aspect | Consistency Guarantee | Expected Lag |
|---|---|---|
| Event store writes | **Strong consistency** (ACID transaction) | 0 ms (committed atomically) |
| Account Command state | Strong consistency | 0 ms |
| Read models (MongoDB) | **Eventual consistency** | < 500ms typical; < 2s worst case |
| Fraud alerts | Near real-time (event-driven) | < 1s typical |
| Notifications | Async best-effort | < 5s typical |

### Convergence Expectations
- Read model rebuild from event replay: O(n) where n = event count per aggregate
- For a typical account with ~100 events: < 10ms rebuild time
- For full read model rebuild (all accounts): minutes, not hours (background job)

### Read-After-Write Consistency
- The API Gateway exposes a 200ms read-after-write window for the frontend
- For critical flows (balance display after transfer), the frontend can poll until convergence
- Documented in `docs/FEATURES/04-transaction-history-query.md`
