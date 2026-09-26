# Feature 06: Notifications

## User Story

As a customer, I want to receive notifications about my account activity so that I stay informed.

## Acceptance Criteria

- [ ] Notification Service consumes `TransferCompleted`, `MoneyDeposited`, `FraudFlagRaised` events
- [ ] Sends email via SMTP (MailHog in dev) with template
- [ ] Sends in-app notification (log-based simulation)
- [ ] POST `/api/notifications/test` sends test notification
- [ ] GET `/api/notifications/history?accountId=` lists notification history
- [ ] Delivery status tracked: `SENT` / `FAILED`
- [ ] Failed emails retried 3 times with exponential backoff
- [ ] Notification templates support variables: `{accountHolderName}`, `{amount}`, `{balance}`, `{transferId}`

## Services / Events / Endpoints Touched

| Component | Detail |
|---|---|
| Kafka | Consumer for relevant events |
| Notification | Template engine, SMTP client, delivery log |
| MailHog | Dev SMTP server (port 1025/8025) |
| API | GET `/api/notifications/history`, POST `/api/notifications/test` |

## Edge Cases

- Invalid email in account → log warning, skip email channel
- MailHog down → retry, then mark FAILED
- High volume → batch processing
- Duplicate events → idempotent delivery (don't send twice)
- Template rendering failure → log error, mark FAILED

## Definition of Done

- [ ] Unit tests: template rendering, event→channel mapping
- [ ] Integration test: MailHog container, verify email received
- [ ] Contract test for `NotificationSent` event
- [ ] Retry logic test: simulate SMTP failure → retry → success
- [ ] OpenAPI spec updated
- [ ] Docs updated
- [ ] Manual: trigger transfer → check MailHog UI for email