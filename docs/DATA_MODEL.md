# Data Model (v2)

> **Source of truth:** `HLD_LLD_SystemDesign_v2.md` — schemas from §3.2, §4.3, §6, §7.
> Table names, columns, and constraints below match v2 exactly. For the fragmentation
> rationale and region-resolution flow see `docs/FRAGMENTATION_DESIGN.md`.

---

## 1. Loan Service tables (v2 §3.2)

```sql
-- Owned by Loan Service
CREATE TABLE admin_pool (
  pool_id UUID PRIMARY KEY,
  available_balance NUMERIC(18,2) NOT NULL,
  version INT NOT NULL                    -- optimistic concurrency, mirrors event-store pattern
);

CREATE TABLE loan_requests (
  loan_id       UUID PRIMARY KEY,
  account_id    UUID NOT NULL,
  amount        NUMERIC(18,2) NOT NULL,
  trust_tier    SMALLINT NOT NULL,        -- 0=Low .. 3=Excellent, snapshotted at request time
  requested_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  status        VARCHAR(20) NOT NULL      -- QUEUED, WAITING_FOR_FUNDS, APPROVED, DISBURSED, REJECTED
);
```

Runtime companions of these tables (not SQL-resident, but part of the loan data model):

- **`loan:queue`** — Redisson `RScoredSortedSet` on Valkey. Score =
  `(3 - trustTier) * 10_000_000_000L + requestedAtEpochMillis`; lower score pops first
  (v2 §3.3).
- **`admin_pool:balance`** — the Valkey mirror of `admin_pool.available_balance` that the
  atomic Lua script debits (`EVAL`): approved (`1`) or insufficient (`0`) (v2 §3.4).

## 2. Reservation tables (v2 §4.3)

```sql
CREATE TABLE reservations (
  reservation_id UUID PRIMARY KEY,
  account_id     UUID NOT NULL,
  amount         NUMERIC(18,2) NOT NULL,
  status         VARCHAR(20) NOT NULL,   -- RESERVED, CAPTURED, RELEASED, EXPIRED
  expires_at     TIMESTAMPTZ NOT NULL,
  captured_at    TIMESTAMPTZ
);
CREATE UNIQUE INDEX idx_reservation_capture ON reservations (reservation_id) WHERE status = 'CAPTURED';
```

The partial unique index is the database-level backstop for the capture idempotency
check: at most one `CAPTURED` row can ever exist per `reservation_id`, so a replayed
offline token cannot double-spend even if the application-level check races (v2 §4.2
step 5). Signed offline tokens are Nimbus JOSE+JWT JWTs containing
`{reservationId, accountId, amount, expiresAt}`; they are not stored server-side beyond
the reservation row itself.

## 3. Concurrency-hygiene tables (v2 §6)

```sql
-- Event store uniqueness guard (from v1, retained)
-- lives on ledger_events:
--   UNIQUE(aggregate_id, version)

-- Idempotency keys on every state-changing API call
CREATE TABLE processed_requests (
  key                VARCHAR(64) PRIMARY KEY,
  response_snapshot  JSONB NOT NULL,
  expires_at         TIMESTAMPTZ NOT NULL
);
-- expired rows purged after 24h; a retried call with the same key
-- replays the original result instead of double-processing
```

Plus on `ledger_events` (see §4 below): the append-only event log with
`UNIQUE(aggregate_id, version)` — still the ultimate source-of-truth guard even with the
queue/lock layers; those layers reduce *contention*, this constraint guarantees
*correctness* even if they somehow failed (v2 §6).

## 4. Account data — hybrid fragmentation (v2 §7)

Within each regional shard, account data is split **vertically** (v2 §7.2):

| Fragment | Contents | Why split out |
|---|---|---|
| `account_core` | accountId, status, region, openedAt | Small, hot, read on almost every request |
| `account_pii` | holderName, KYC documents, registration IP, address | Sensitive, infrequently read, candidate for stricter access control/encryption at rest |
| `ledger_events` | the append-only event log itself | Extremely high write volume, never updated — benefits from being physically isolated from the slower-changing tables above so its I/O pattern doesn't contend with them |

`ledger_events` columns (event-store standard, carried over from v1):

| Column | Type | Notes |
|---|---|---|
| `event_id` | UUID PK | |
| `aggregate_id` | UUID | Account aggregate; `UNIQUE(aggregate_id, version)` |
| `version` | INT | Optimistic-concurrency version |
| `event_type` | VARCHAR | e.g. `AccountOpened`, `MoneyDeposited` (see `docs/EVENT_CATALOG.md`) |
| `payload` | JSONB | Event body |
| `region` | VARCHAR | `APAC` \| `EU` \| `AMER` — **horizontal sharding key** (v2 §7.1) |
| `occurred_at` | TIMESTAMPTZ | |

## 5. Trust Score table

Trust Score Service maintains (v2 §3.6):

| Column | Type | Notes |
|---|---|---|
| `account_id` | UUID PK | |
| `score` | INT | 0–100 |
| `tier` | SMALLINT | 0=Low (0–40), 1=Medium (41–70), 2=High (71–90), 3=Excellent (91–100) |
| `last_changed_at` | TIMESTAMPTZ | Last `TrustScoreChanged` |

## 6. Read model (MongoDB / FerretDB)

Ledger Query Service projects account events into document collections
(`accounts`, `transactions`). Engine choice — MongoDB vs. FerretDB vs. Postgres JSONB —
is an open decision: see `docs/OPEN_DECISIONS.md`.

## 7. ShardingSphere sharding rule config (v2 §7.1)

```yaml
rules:
  - !SHARDING
    tables:
      ledger_events:
        actualDataNodes: ds_${region}.ledger_events
        databaseStrategy:
          standard:
            shardingColumn: region
            shardingAlgorithmName: region-inline
    shardingAlgorithms:
      region-inline:
        type: INLINE
        props:
          algorithm-expression: ds_${region}
```

Application code writes `INSERT INTO ledger_events ...` against a *logical* table;
ShardingSphere transparently routes each row to `ds_apac`, `ds_eu`, or `ds_amer`
(**fragmentation transparency**, v2 §7.1). Physical data sources:

| Datasource | Database | Contents |
|---|---|---|
| `ds_apac` | `ddbs_apac` | `account_core`, `account_pii`, `ledger_events` (APAC rows) |
| `ds_eu` | `ddbs_eu` | same schema, EU rows |
| `ds_amer` | `ddbs_amer` | same schema, AMER rows |

Loan Service tables (`admin_pool`, `loan_requests`), `reservations`, and
`processed_requests` are **not sharded** — they are single-database operational tables
owned by their respective services.

## 8. Event-store projection map

| Store | Written by | Consumed by |
|---|---|---|
| `ledger_events` (sharded) | Account Command Service | Ledger Query (replay/recovery), all consumers via Kafka |
| `admin_pool`, `loan_requests` | Loan Service | Loan Service |
| `reservations` | Payment & Reservation Service | Payment & Reservation Service |
| `processed_requests` | every state-changing API service | same service |
| `trust_scores` | Trust Score Service | Loan Service (via `TrustScoreChanged` → local cache) |
| MongoDB/FerretDB read model | Ledger Query Service | Ledger Query Service HTTP surface |
