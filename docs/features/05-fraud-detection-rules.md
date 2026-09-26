# Feature 05: Fraud Detection Rules

## User Story

As a bank, I want to detect fraudulent transactions in near real time so that I can flag them and alert users.

## Acceptance Criteria

- [ ] Fraud Detection Service subscribes to all money movement events
- [ ] Rule engine evaluates each event against configured rules
- [ ] Velocity check: >5 transactions from same account in 1 minute → flag
- [ ] High-value check: single transaction > $10,000 → flag (configurable threshold)
- [ ] Geo/time check (stretch): transactions outside normal hours → flag
- [ ] `FraudFlagRaised` event emitted when rule triggers
- [ ] `FraudFlagCleared` event emitted when cleared manually or automatically
- [ ] GET `/api/fraud/alerts` lists active alerts with severity
- [ ] POST `/api/fraud/alerts/{id}/clear` clears an alert
- [ ] Rule config is reloadable without restart

## Services / Events / Endpoints Touched

| Component | Detail |
|---|---|
| Kafka | Consumer for all money movement events |
| Fraud | Rule engine, alert storage |
| Kafka | Publisher of `FraudFlagRaised`, `FraudFlagCleared` |
| Notification | Consumes fraud events, sends alert emails |
| API | GET `/api/fraud/alerts`, POST `/api/fraud/alerts/{id}/clear` |

## Rule Definitions (Initial Set)

| Rule | Description | Severity | Threshold |
|---|---|---|---|
| Velocity | >5 txns in 1 minute | MEDIUM | 5/minute |
| High Value | Single txn > $10,000 | HIGH | 10,000 |
| Rapid Withdrawal | 3+ withdrawals in 5 min | HIGH | 3/5min |
| Foreign Currency | Txn in different currency than account | LOW | n/a |

## Edge Cases

- Simultaneous rules trigger → merge into single alert or list?
- Fraud flag on completed transfer → compensation should clear flag
- False positive → manual clear endpoint
- Rule threshold change → affects future events only (not historical)
- Service restart → replay events and re-evaluate (no missed flags)

## Definition of Done

- [ ] Unit tests: each rule evaluates correctly on sample events
- [ ] Integration test: consume events, verify alert emission
- [ ] Contract test for `FraudAlert` event schema
- [ ] Testcontainers: Kafka + PostgreSQL for rule evaluation
- [ ] API tests for alert listing/clearing
- [ ] OpenAPI spec updated
- [ ] Docs updated
- [ ] Manual: send high-value transaction → verify alert in API