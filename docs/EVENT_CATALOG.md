# Event Catalog

All domain events are immutable, append-only entries in the event store and are also published to Kafka for async consumption.

## Event Schema Convention

```json
{
  "eventId": "uuid",
  "aggregateId": "uuid",
  "aggregateType": "Account | Transfer",
  "eventType": "AccountOpened | MoneyDeposited | ...",
  "payload": { ... },
  "metadata": {
    "userId": "string",
    "timestamp": "ISO-8601",
    "version": 1
  }
}
```

## Event Table Schema (PostgreSQL)

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

-- Optimistic concurrency: one row per (aggregate_id, version)
CREATE UNIQUE INDEX idx_events_aggregate_version ON events(aggregate_id, version);
-- Fast lookup by aggregate
CREATE INDEX idx_events_aggregate ON events(aggregate_id, occurred_at);
-- Fast lookup by event type for consumers
CREATE INDEX idx_events_type ON events(event_type, occurred_at);
```

**Optimistic concurrency enforcement:** When appending events for an aggregate, the expected version must match the current max version in the store. If a conflict is detected, the command is rejected — this prevents double-application from retries or out-of-order delivery.

**Event ordering per aggregate:** Guaranteed by the `version` sequence number within each `aggregate_id`. The event store enforces monotonically increasing version per aggregate. Global ordering across aggregates is not guaranteed; consumers must handle events from different aggregates independently.

---

## Domain Events

### Account Lifecycle

| Event | Producing Service | Version | Consumers |
|---|---|---|---|
| `AccountOpened` | Account Command | 1 | Ledger Query, Fraud Detection, Notification |
| `AccountClosed` | Account Command | 1 | Ledger Query, Notification |

**AccountOpened payload:**
```json
{
  "accountId": "uuid",
  "accountHolderName": "string",
  "email": "string",
  "initialBalance": 0.00,
  "currency": "USD",
  "openedAt": "2026-09-26T00:00:00Z"
}
```

---

### Money Movement

| Event | Producing Service | Version | Consumers |
|---|---|---|---|
| `MoneyDeposited` | Account Command | 1 | Ledger Query, Fraud Detection, Notification |
| `MoneyWithdrawn` | Account Command | 1 | Ledger Query, Fraud Detection, Notification |
| `WithdrawalFailed` | Account Command | 1 | Notification |

**MoneyDeposited payload:**
```json
{
  "accountId": "uuid",
  "amount": 100.00,
  "currency": "USD",
  "balanceAfter": 100.00,
  "depositedAt": "2026-09-26T00:00:00Z",
  "reference": "string"
}
```

**MoneyWithdrawn payload** — same shape, with `withdrawnAt` and `balanceAfter`.

**WithdrawalFailed payload:**
```json
{
  "accountId": "uuid",
  "amount": 500.00,
  "reason": "INSUFFICIENT_FUNDS",
  "failedAt": "2026-09-26T00:00:00Z"
}
```

---

### Transfers (Saga)

| Event | Producing Service | Version | Consumers |
|---|---|---|---|
| `TransferInitiated` | Account Command | 1 | Ledger Query, Fraud Detection, Notification |
| `TransferCompleted` | Account Command | 1 | Ledger Query, Fraud Detection, Notification |
| `TransferFailed` | Account Command | 1 | Ledger Query, Notification |
| `TransferCompensated` | Account Command | 1 | Ledger Query, Notification |

**TransferInitiated payload:**
```json
{
  "transferId": "uuid",
  "sourceAccountId": "uuid",
  "destinationAccountId": "uuid",
  "amount": 250.00,
  "currency": "USD",
  "status": "PENDING",
  "initiatedAt": "2026-09-26T00:00:00Z"
}
```

**TransferCompleted payload:**
```json
{
  "transferId": "uuid",
  "sourceAccountId": "uuid",
  "destinationAccountId": "uuid",
  "amount": 250.00,
  "currency": "USD",
  "sourceBalanceAfter": 750.00,
  "destinationBalanceAfter": 1250.00,
  "completedAt": "2026-09-26T00:00:00Z"
}
```

**TransferFailed payload:**
```json
{
  "transferId": "uuid",
  "sourceAccountId": "uuid",
  "destinationAccountId": "uuid",
  "amount": 250.00,
  "currency": "USD",
  "failureReason": "INSUFFICIENT_FUNDS | TIMEOUT | FRAUD_FLAG",
  "failedAt": "2026-09-26T00:00:00Z"
}
```

**TransferCompensated payload:**
```json
{
  "transferId": "uuid",
  "sourceAccountId": "uuid",
  "destinationAccountId": "uuid",
  "amount": 250.00,
  "compensatedAt": "2026-09-26T00:00:00Z",
  "reason": "string"
}
```

---

### Fraud

| Event | Producing Service | Version | Consumers |
|---|---|---|---|
| `FraudFlagRaised` | Fraud Detection | 1 | Notification |
| `FraudFlagCleared` | Fraud Detection | 1 | Notification |

**FraudFlagRaised payload:**
```json
{
  "flagId": "uuid",
  "accountId": "uuid",
  "transferId": "uuid | null",
  "severity": "LOW | MEDIUM | HIGH | CRITICAL",
  "rule": "string",
  "description": "string",
  "raisedAt": "2026-09-26T00:00:00Z"
}
```

---

### Notifications

| Event | Producing Service | Version | Consumers |
|---|---|---|---|
| `NotificationSent` | Notification | 1 | (none — terminal) |

**NotificationSent payload:**
```json
{
  "notificationId": "uuid",
  "accountId": "uuid",
  "channel": "EMAIL | WEBHOOK | IN_APP",
  "subject": "string",
  "body": "string",
  "sentAt": "2026-09-26T00:00:00Z",
  "status": "SENT | FAILED"
}
```

---

## Event Versioning Strategy

1. **Backward-compatible changes** (new optional fields in payload): bump minor version of the event type (e.g., `MoneyDeposited` v2). Old consumers ignore unknown fields (Jackson `FAIL_ON_UNKNOWN_PROPERTIES=false`).
2. **Breaking changes** (field renamed/removed): introduce new event type (e.g., `MoneyDepositedV2`) with a migration window where both event types are produced.
3. All events include a top-level `metadata.version` (integer) indicating the schema version of that event instance.
4. The event store retains all versions — no in-place mutation of events.
