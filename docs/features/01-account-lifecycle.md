# Feature 01: Account Lifecycle

## User Story

As a system user, I want to open a new bank account so that I can start receiving deposits and making transfers.

## Acceptance Criteria

- [ ] POST `/api/accounts` with `{accountHolderName, email, initialDeposit, currency}` creates a new account
- [ ] Returns `201 Created` with `{accountId, status: OPEN, openedAt}`
- [ ] `AccountOpened` event persisted in event store and published to Kafka
- [ ] Ledger Query read model updated with new account balance
- [ ] Duplicate email rejected with `409 Conflict`
- [ ] Negative initial deposit rejected with `400 Bad Request`
- [ ] Account status defaults to `OPEN`
- [ ] Event store version increments correctly per aggregate

## Services / Events / Endpoints Touched

| Layer | Component | Detail |
|---|---|---|
| API | POST `/api/accounts` | Gateway → Account Command |
| Command | `AccountCommandHandler` | Validates, creates aggregate, appends `AccountOpened` |
| Event Store | `events` table | Append `AccountOpened` with version 1 |
| Kafka | Topic `bank-events` | Publish `AccountOpened` |
| Ledger Query | `balances` collection | Insert new balance document |
| Fraud | `FraudDetectionService` | Register new account for monitoring |

## Edge Cases

- Empty account holder name → `400`
- Invalid email format → `400`
- Email already exists → `409`
- Initial deposit > 0,000,000 (unrealistic) → `400` or warn
- Concurrent account creation for same email → `409`

## Definition of Done

- [ ] Unit tests for `AccountOpened` event application (≥ 3 cases)
- [ ] Integration test with Testcontainers (PostgreSQL + Kafka)
- [ ] Contract test for `OpenAccountRequest` / `OpenAccountResponse` schema
- [ ] OpenAPI spec updated
- [ ] README / docs updated
- [ ] `mvn test` passes for Account Command Service
- [ ] Manual verification: `curl -X POST http://localhost:8080/api/accounts -d '{...}'`
