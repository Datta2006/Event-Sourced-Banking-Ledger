# Event Catalog (v2)

> **Source of truth:** `HLD_LLD_SystemDesign_v2.md`. Event names below match v2 exactly
> (`FundsReserved`, `FundsCaptured`, `FundsReleased`, `LoanApproved`, `LoanDisbursed`,
> `LoanRepaidOnTime`/`LoanRepaidLate`, `LoanDefaulted`, `AdminPoolReplenished`,
> `TrustScoreChanged`, plus the carried-over account events). All payloads are JSON
> serialized with `JsonSerializer`; every event carries `eventId`, `aggregateId`,
> `version`, `occurredAt`.

---

## 1. Topics

| Topic | Partitioning | Producers | Consumers |
|---|---|---|---|
| `account.commands` | key = `accountId` (partition-per-account) | API-facing services publishing withdrawal/deposit/transfer **commands** (v2 §5.2 allows publishing into `account.events` instead — see `docs/OPEN_DECISIONS.md`) | Account Command Service |
| `account.events` | key = `accountId` (partition-per-account) | Account Command Service | Ledger Query, Trust Score, Fraud Detection, Notification |
| `loan.events` | key = `accountId` / `loanId` | Loan Service | Trust Score, Fraud Detection, Notification, Ledger Query |
| `payment.events` | key = `accountId` / `reservationId` | Payment & Reservation Service | Trust Score, Fraud Detection, Notification, Ledger Query |
| `trust-score.events` | key = `accountId` | Trust Score Service | Loan Service (local tier cache), Notification |

Kafka guarantees only one partition holds all messages for a given key, and only one
consumer instance processes a given partition at a time — FCFS ordering happens at the
broker (v2 §5.2).

---

## 2. Account events — topic `account.events`

Carried over from v1. Appended to the region-sharded `ledger_events` store by
Account Command Service (optimistic concurrency: `UNIQUE(aggregate_id, version)`, v2 §6).

| Event | Payload (beyond envelope) | Notes |
|---|---|---|
| `AccountOpened` | `holderName`, `region`, `registrationIp`, `openedAt` | Region resolved at open time from registration IP via ip-location-db; `region` becomes the shard key (v2 §7.1) |
| `AccountClosed` | `closedAt`, `reason` | Carried over from v1 account lifecycle |
| `MoneyDeposited` | `accountId`, `amount`, `currency` | Trust Score: +0.5 per ₹10,000 deposited, capped +5/day (v2 §3.6) |
| `MoneyWithdrawn` | `accountId`, `amount`, `currency` | Written under Redisson `RFairLock("account-lock:" + accountId)` in the withdrawal path (v2 §5.2) |

## 3. Loan events — topic `loan.events`

New in v2 (§3). Loan Service is the sole producer.

| Event | Payload | Notes |
|---|---|---|
| `LoanApproved` | `loanId`, `accountId`, `amount`, `trustTier` | Emitted after the atomic Lua pop-and-debit of `admin_pool:balance` succeeds (v2 §3.4) |
| `LoanDisbursed` | `loanId`, `accountId`, `amount`, `disbursedAt` | Follows `LoanApproved`; `loan_requests.status = DISBURSED`. Trust Score treats this as "LoanTaken (disbursed)": −5, recovered on repayment (v2 §3.4, §3.6) |
| `LoanRepaidOnTime` | `loanId`, `accountId`, `amount` | Trust Score: +10 (v2 §3.6). Also triggers `AdminPoolReplenished` |
| `LoanRepaidEarly` | `loanId`, `accountId`, `amount` | Trust Score: +15 (v2 §3.6). Also triggers `AdminPoolReplenished` |
| `LoanRepaidLate` | `loanId`, `accountId`, `amount` | Trust Score: −8 (v2 §3.6). Also triggers `AdminPoolReplenished` |
| `LoanDefaulted` | `loanId`, `accountId`, `amountOutstanding` | Trust Score: −20 (v2 §3.6) |
| `AdminPoolReplenished` | `poolId`, `amount`, `reason` (`LOAN_REPAID` \| `ADMIN_DEPOSIT`) | E.g., a loan gets repaid or the admin deposits more; Loan Service re-checks the `WAITING_FOR_FUNDS` list, highest-priority first (v2 §3.4) |

## 4. Payment & reservation events — topic `payment.events`

New in v2 (§4). Payment & Reservation Service is the sole producer.

| Event | Payload | Notes |
|---|---|---|
| `FundsReserved` | `reservationId`, `accountId`, `amount`, `expiresAt` | Pessimistic pre-commit: moves `amount` from `available_balance` to `reserved_balance` with optimistic concurrency like v1 (v2 §4.2 step 1) |
| `FundsCaptured` | `reservationId`, `accountId`, `merchantAccountId`, `amount`, `capturedAt` | First capture by `reservationId` wins — any replay of the same token is rejected (idempotency table + partial unique index, v2 §4.2 step 5, §4.3) |
| `FundsReleased` | `reservationId`, `accountId`, `amount`, `reason` (`EXPIRED` \| `MERCHANT_DECLINED`) | Scheduled TTL job returns uninvoked holds to `available_balance` (v2 §4.2 step 7) |

## 5. Trust score events — topic `trust-score.events`

| Event | Payload | Notes |
|---|---|---|
| `TrustScoreChanged` | `accountId`, `previousScore`, `newScore`, `previousTier`, `newTier`, `cause` | Tiers: 0–40 Low, 41–70 Medium, 71–90 High, 91–100 Excellent (v2 §3.6). Loan Service consumes this to refresh its local read-only tier cache — no synchronous cross-service call at loan-request time |

## 6. Commands (not events) — topic `account.commands`

Commands are requests to change state, published by edge services and consumed by
Account Command Service (v2 §5.2). They are not appended to `ledger_events`.

| Command | Payload | Notes |
|---|---|---|
| `DepositCommand` | `accountId`, `amount`, `idempotencyKey` | |
| `WithdrawCommand` | `accountId`, `amount`, `idempotencyKey` | Keyed by `accountId` → partition-per-account ordering; guarded by `RFairLock` at the consumer (v2 §5.2–§5.3) |
| `TransferCommand` | `fromAccountId`, `toAccountId`, `amount`, `idempotencyKey` | Drives the transfer saga (see `docs/SAGA_DESIGN.md`) |

## 7. Transfer saga orchestration events — topic `account.events`

Carried over from v1 (exact v1 names retained; flagged in `docs/OPEN_DECISIONS.md`).
The underlying ledger mutations are ordinary `MoneyWithdrawn` / `MoneyDeposited` events.

| Event | Payload | Notes |
|---|---|---|
| `TransferInitiated` | `transferId`, `fromAccountId`, `toAccountId`, `amount` | Saga starts |
| `TransferSourceDebited` | `transferId`, `fromAccountId`, `amount` | Source leg done |
| `TransferTargetCredited` | `transferId`, `toAccountId`, `amount` | Target leg done |
| `TransferCompleted` | `transferId` | Saga finished successfully |
| `TransferFailed` | `transferId`, `reason` | Saga aborted |
| `TransferSourceReimbursed` | `transferId`, `fromAccountId`, `amount` | Compensation: money returned to source after target-leg failure |

## 8. Consumer effects summary (Trust Score, v2 §3.6)

| Event | Effect on trust score |
|---|---|
| `MoneyDeposited` | +0.5 per ₹10,000 deposited (capped +5/day) |
| `LoanRepaidOnTime` | +10 |
| `LoanRepaidEarly` | +15 |
| `LoanTaken` (`LoanDisbursed`) | −5 (recovered on repayment) |
| `LoanRepaidLate` | −8 |
| `LoanDefaulted` | −20 |
