# Feature 03: Money Transfer Saga

## User Story

As an account holder, I want to transfer money to another account so that I can pay others.

## Acceptance Criteria

- [ ] POST `/api/accounts/transfer` with `{sourceAccountId, destinationAccountId, amount, currency, description}` returns `200` with `transferId` and status `INITIATED`
- [ ] Transfer saga starts: source account debited (pending), destination credited, both events persisted
- [ ] On success: `TransferCompleted` event emitted, balances updated in read model
- [ ] On failure (insufficient funds, account closed, timeout): `TransferFailed` event emitted, no money moved
- [ ] Saga is idempotent: retrying same transfer returns same transferId
- [ ] Fraud check runs asynchronously on TransferInitiated
- [ ] If fraud flag raised, saga compensates (refund source) and emits `TransferCompensated`
- [ ] Notification service sends confirmation/completion emails
- [ ] Ledger Query shows transfer in statements for both accounts

## Services / Events / Endpoints Touched

| Component | Detail |
|---|---|
| API | POST `/api/accounts/transfer` (gateway → command) |
| Command | Transfer saga orchestrator + handlers |
| Event Store | `TransferInitiated`, `MoneyWithdrawn` (pending), `MoneyDeposited`, `TransferCompleted` / `TransferFailed` / `TransferCompensated` |
| Kafka | Publish all events |
| Ledger Query | Update balances, statements, transfers collection |
| Fraud | Consume `TransferInitiated`, apply rules, may emit `FraudFlagRaised` |
| Notification | Consume transfer/fraud events, send emails |

## Edge Cases

- Same source and destination account → `400 Bad Request`
- Source or destination account not found → `404 Not Found`
- Source account closed → `409 Conflict`
- Network partition during saga → retry logic with exponential backoff
- Saga step timeout (30s) → trigger compensation
- Concurrent transfers on same account → optimistic concurrency on account version
- Very small amount (0.01) → allowed
- Amount with > 2 decimal places → rejected (currency precision)

## Definition of Done

- [ ] Unit tests: saga state machine transitions (success/failure paths)
- [ ] Integration test: Testcontainers with 2 accounts, simulate success/failure
- [ ] Contract test for transfer request/response
- [ ] Idempotency test: send same transfer twice → same transferId, no double-apply
- [ ] Compensation test: simulate fraud flag → source refunded
- [ ] Timeout test: mock slow downstream service → saga compensates
- [ ] Event replay test: rebuild transfer state from events
- [ ] OpenAPI spec updated
- [ ] Docs updated
- [ ] Manual: curl transfer endpoint, check balances, statements, events in DB