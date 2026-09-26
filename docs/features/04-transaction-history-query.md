# Feature 04: Transaction History Query

## User Story

As a user, I want to view my account balance and transaction history so that I can track my finances.

## Acceptance Criteria

- [ ] GET `/api/ledger/accounts/{id}/balance` returns current balance and currency
- [ ] GET `/api/ledger/accounts/{id}/statements` returns paginated transaction history (default 20/page)
- [ ] Statements support `page`, `size`, `fromDate`, `toDate`, `type` filters
- [ ] Balance reflects all processed events (not just pending)
- [ ] Read model converges within 500ms of event (eventual consistency)
- [ ] Empty account → returns zero balance and empty history
- [ ] Non-existent account → `404 Not Found`
- [ ] Pagination works beyond 1000 events (deep pagination)

## Services / Events / Endpoints Touched

| Component | Detail |
|---|---|
| API | GET `/api/ledger/accounts/{id}/balance` & `/api/ledger/accounts/{id}/statements` |
| Ledger Query | MongoDB read models: `balances`, `statements` |
| Event Store | Replay events for rebuild |
| Kafka | Consumer for event stream |

## Edge Cases

- Very large transaction history → paginate efficiently (cursor-based vs offset)
- Date range filters outside event range → empty result set
- Type filter on invalid type → `400 Bad Request`
- Page beyond available data → empty array, correct `totalElements`
- Concurrent balance read during transfer → eventually consistent (read-after-write window)

## Definition of Done

- [ ] Unit tests: query handlers for balance & statements
- [ ] Integration test: real MongoDB + event replay
- [ ] Contract test for API response schemas
- [ ] Pagination test: page 0 size 10, page 5 size 20, etc.
- [ ] Event replay test: rebuild from scratch matches live
- [ ] OpenAPI spec updated
- [ ] Docs updated
- [ ] Manual: curl balance & statements, verify against events table