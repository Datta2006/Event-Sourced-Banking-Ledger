# Feature Benchmarking — Comparable Open-Source Projects

Based on surveying existing open-source CQRS/event-sourcing banking demos and general banking microservice references.

| Feature | Status | Mapped To | Priority |
|---|---|---|---|
| Open/close account | **Planned** | `01-account-lifecycle.md` | MUST |
| Deposit / Withdraw | **Planned** | `02-deposit-withdrawal.md` | MUST |
| Transfer between accounts | **Planned** | `03-money-transfer-saga.md` | MUST |
| Transaction history query | **Planned** | `04-transaction-history-query.md` | MUST |
| Pagination & date-range filters | **Planned** | `04-transaction-history-query.md` | MUST |
| Event replay to rebuild read model | **Planned** | `07-event-replay-and-recovery.md` | MUST |
| Idempotent command handling | **Planned** | Embedded in all features | MUST |
| Optimistic concurrency on aggregates | **Planned** | Event store versioning | MUST |
| Outbox pattern for reliable publishing | **Planned** | Stretch | SHOULD |
| Fraud / anomaly flags (rules-based) | **Planned** | `05-fraud-detection-rules.md` | MUST |
| Fraud flags on dashboard (near real-time) | **Planned** | Frontend + fraud service | SHOULD |
| Read-model snapshotting | **Stretch** | `07-event-replay-and-recovery.md` | STRETCH |
| Admin/audit view (raw event stream per account) | **Stretch** | `07-event-replay-and-recovery.md` | STRETCH |
| Multi-currency with FX conversion | **Stretch** | `02-deposit-withdrawal.md` | STRETCH |
| Notification templates | **Planned** | `06-notifications.md` | MUST |
| API Gateway routing + auth | **Planned** | `08-api-gateway-and-auth.md` | MUST |
| Frontend dashboard | **Planned** | `09-frontend-dashboard.md` | MUST |
| Docker + Docker Compose deployment | **Planned** | DEPLOYMENT.md | MUST |
| CI (GitHub Actions) | **Planned** | DEPLOYMENT.md | MUST |
| Contract tests between services | **Planned** | TESTING_STRATEGY.md | MUST |
| Health checks + metrics (Actuator) | **Planned** | DEPLOYMENT.md | MUST |
| Structured JSON logging | **Planned** | DEPLOYMENT.md | SHOULD |

## Gap Analysis vs. Reference Projects

| Reference Project | Features They Have | We Have | We Don't |
|---|---|---|---|
| kbastani/event-sourcing-microservices-example | CQRS, Kafka, React, Docker | All above | React frontend, admin views |
| Slimani-CE/compte-cqrs-event-sourcing | CQRS, Kafka, MongoDB, Vue | All above | Vue→React, admin views |
| robinhosz/cqrs-eventsourcing-springboot | CQRS, Kafka, MySQL, MongoDB | All above | Frontend |
| adityagarde/spring-bank-microservices-app | CQRS, Kafka, RabbitMQ options | All above | RabbitMQ option |
| fuinorg/ddd-cqrs-4-java-example | CQRS, EventStoreDB | All above | EventStoreDB option |

## Backlog Priority Tags

- **MUST** — required for rubric / core project
- **SHOULD** — nice to have, strengthens demo
- **STRETCH** — only if time allows after MUST + SHOULD

The team should review this list in the first planning meeting and agree on scope before implementation begins.