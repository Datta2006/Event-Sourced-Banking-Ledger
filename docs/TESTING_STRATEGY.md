# Testing Strategy (v2)

> **Source of truth:** `HLD_LLD_SystemDesign_v2.md`. Extends the v1 test pyramid with
> Testcontainers spinning up **Valkey** and **a second Postgres shard** in CI (v2 §0),
> plus the three required concurrency/offline tests (§3 below). Scaffolding stage —
> no test code exists yet; this document is the contract the tests must satisfy.

---

## 1. Test pyramid

| Level | Scope | Tools | Examples |
|---|---|---|---|
| Unit | domain logic in isolation | JUnit 5 | score formula `(3 - trustTier) * 10^9 + requestedAtEpochMillis`; trust-tier boundaries (40/41, 70/71, 90/91); token payload `{reservationId, accountId, amount, expiresAt}` |
| Integration | one service + its stores | JUnit 5 + Testcontainers (Postgres, Valkey, Kafka, MongoDB) | event append with `UNIQUE(aggregate_id, version)`; `processed_requests` replay; reservation capture idempotency |
| System | multi-service via API Gateway | Testcontainers compose + REST clients | full deposit → withdraw → statement flow; loan request → approval |
| Concurrency (new in v2) | multi-instance contention | Testcontainers (Valkey + ≥2 Postgres shards) + parallel executors | the three tests in §3 |
| Recovery | replay & resilience | Testcontainers | rebuild read model from sharded `ledger_events`; consumer restart |

## 2. Infrastructure rules for concurrency tests

- Real **Valkey** container — Lua-script atomicity and Redisson `RFairLock` /
  `RScoredSortedSet` semantics are exactly what's under test; mocking them would test
  nothing.
- **At least two Postgres shard containers** (`ds_apac`, `ds_eu`) so ShardingSphere
  routing is exercised against physical targets, not a single database.
- At least **two service instances** where the scenario demands multi-server behavior
  (loan worker, withdrawal consumer).
- Determinism comes from ordering guarantees (Kafka partition order, fair-lock queue
  order), never from `Thread.sleep`.

## 3. Required v2 concurrency/offline tests

### 3.1 Loan pool contention — exactly one approval

> Fires two simultaneous loan requests against a pool that can only satisfy one and
> asserts exactly one is approved.

- **Setup:** admin pool = ₹2,000 (`admin_pool:balance` in Valkey = 2000, matching
  `admin_pool.available_balance`); two Loan Service worker instances connected to the
  same Valkey.
- **Action:** both instances concurrently submit a ₹2,000 loan request for two different
  accounts of **equal trust tier** (equal tier forces the FCFS tiebreak of v2 §3.3).
- **Assert:**
  1. exactly one loan ends `DISBURSED` (`LoanApproved` + `LoanDisbursed` emitted once);
  2. the other ends `WAITING_FOR_FUNDS` — not rejected, not approved;
  3. final pool balance = 0, and `admin_pool.available_balance` (Postgres) agrees with
     `admin_pool:balance` (Valkey);
  4. after an `AdminPoolReplenished` event, the waiting request is re-checked and
     becomes the next approval (highest-priority first).

### 3.2 Multi-server withdrawal — FCFS ordering

> Fires two simultaneous withdrawals from two simulated server instances and asserts
> FCFS ordering.

- **Setup:** account X with balance covering exactly **one** ₹1,500 withdrawal; two
  simulated app-server instances publishing `WithdrawCommand`s to the Kafka topic keyed
  by `accountId`; one Account Command consumer instance per partition.
- **Action:** instance 1 publishes `WithdrawCommand(acc=X, amt=1500)` at t=0ms; instance
  2 publishes the same at t=4ms (v2 §5.3 timing).
- **Assert:**
  1. the command the broker received first is applied first — `MoneyWithdrawn` appended
     with `version = n`, then rejection for the second;
  2. final balance = 0 with exactly one `MoneyWithdrawn` in `ledger_events`;
  3. both commands were delivered **in broker-received order** to the same consumer
     (partition-per-account), and the `RFairLock` acquisition order matches arrival
     order (fairness, not barging — v2 §5.2);
  4. the optimistic-concurrency constraint would reject any out-of-order append even if
     the lock layer failed (`UNIQUE(aggregate_id, version)`, v2 §6).

### 3.3 Offline payment — double capture rejected

> Captures the same reservation token twice and asserts the second capture is rejected.

- **Setup:** account with sufficient balance; `POST /api/reservations` creates a
  reservation (`FundsReserved`, amount moved available → reserved) and mints a signed
  Nimbus JOSE+JWT offline token; merchant device "goes offline".
- **Action:** with connectivity restored, call
  `POST /api/reservations/{id}/capture` **twice** with the same token — simulating the
  same QR code shown to two merchants (the classic offline double-spend, v2 §4.2).
- **Assert:**
  1. first capture succeeds: `FundsCaptured` emitted once, reservation `status = CAPTURED`,
     `captured_at` set, funds land in the merchant account;
  2. second capture is rejected with **409 Conflict** and emits no second
     `FundsCaptured` — the redeemed idempotency check keyed by `reservationId` plus the
     partial unique index `idx_reservation_capture` hold;
  3. the account is debited exactly once (reserved amount converted to a real debit
     once);
  4. a capture after TTL expiry returns **410 Gone** (`FundsReleased` already emitted;
     v2 §4.2 step 7).

## 4. Additional concurrency & hygiene tests (v2 §6)

| Test | Asserts |
|---|---|
| Optimistic concurrency | two appends at the same `version` for one aggregate → one succeeds, one violates `UNIQUE(aggregate_id, version)` |
| Idempotency-key replay | same `Idempotency-Key` on a retried call returns the original response, no double-processing; key expires after 24h |
| Trust score cache drift | Loan Service prices a request from its local tier cache (eventually consistent), not by a synchronous cross-service call (v2 §3.6) |
| Sharding transparency | rows written via the logical `ledger_events` insert land in the correct physical shard per `region`; app code sees no shard details (v2 §7.1) |
| Vertical split integrity | `account_pii` is not reachable through `account_core` queries — the column split is real, not just naming (v2 §7.2) |

## 5. What we deliberately do *not* test yet

Scaffolding stage: no business logic exists, so all tests in this document are
forward-looking contracts. When implementation starts (v2 §9 order), each mechanism's
test lands with the mechanism, and the three tests in §3 gate their features.
