# Feature 02: Deposit & Withdrawal

## User Story

As an account holder, I want to deposit and withdraw money from my account so that I can manage my funds.

## Acceptance Criteria

Deposits:
- [ ] POST `/api/accounts/{id}/deposit` with `{amount, reference, currency}` succeeds for amount > 0
- [ ] Returns `200 OK` with updated balance and eventId
- [ ] `MoneyDeposited` event persisted with correct `balanceAfter`
- [ ] Ledger Query balance and statements updated
- [ ] Negative or zero amount → `400 Bad Request`

Withdrawals:
- [ ] POST `/api/accounts/{id}/withdraw` with `{amount, reference}` succeeds if sufficient funds
- [ ] Returns `200 OK` with updated balance
- [ ] `MoneyWithdrawn` event persisted
- [ ] Ledger Query updated
- [ ] Insufficient funds → `409 Conflict` with reason
- [ ] Withdrawal from closed account → `409 Conflict`

## Services / Events / Endpoints Touched

| Component | Detail |
|---|---|
| API | POST `/api/accounts/{id}/deposit` & `/api/accounts/{id}/withdraw` |
| Command | Validate account exists/open, check balance, append event |
| Event Store | `MoneyDeposited` / `MoneyWithdrawn` |
| Kafka | Publish to `bank-events` |
| Ledger Query | Update balance, append to statements |
| Fraud | Check velocity/threshold rules on deposit/withdrawal |
| Notification | Send confirmation email if configured |

## Edge Cases

- Maximum precision (2 decimal places) enforced
- Very large amounts (> 1M) allowed but monitored by fraud
- Concurrent deposits/withdrawals → optimistic concurrency conflict → retry
- Withdrawal that brings balance to exactly zero → allowed
- Reference field optional, max length 100 chars

## Definition of Done

- [ ] Unit tests: deposit/withdraw valid & invalid cases
- [ ] Integration test: concurrent operations trigger version conflict
- [ ] Contract test for request/response schemas
- [ ] Event replay test: rebuild balance from events matches live
- [ ] OpenAPI spec updated
- [ ] Docs updated
- [ ] Manual: curl both endpoints, verify balance, check statements