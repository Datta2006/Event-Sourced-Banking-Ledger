# Open Decisions — Team To Confirm

This file tracks deliberate open decisions that need team consensus during the first planning meeting. Items here are documented but not locked.

| # | Decision | Options | Decided? |
|---|---|---|---|
| 1 | **Frontend framework:** React (Vite) vs Angular | React is default per the locked tech stack; Angular if team knows it better | ☐ Team to confirm |
| 2 | **Build tool:** Maven vs Gradle | Maven is default; Gradle if team prefers | ☐ Team to confirm |
| 3 | **Event store:** Postgres table vs EventStoreDB | Postgres is default; EventStoreDB as stretch | ☐ Team to confirm |
| 4 | **Kafka vs Redpanda** for local dev | Kafka (Confluent) is default; Redpanda as lighter drop-in | ☐ Team to confirm |
| 5 | **Auth scope:** Spring Security + JWT only vs Keycloak | JWT at gateway is default; Keycloak stretch | ☐ Team to confirm |
| 6 | **Observability:** Actuator baseline vs Prometheus+Grafana | Actuator is default; Prometheus/Grafana stretch | ☐ Team to confirm |
| 7 | **Fraud detection:** Rule-based vs ML model | Rule-based is default; ML stretch goal | ☐ Team to confirm |
| 8 | **Notifications:** MailHog only vs Novu | MailHog is default; Novu stretch | ☐ Team to confirm |
| 9 | **Currency support:** Single USD vs multi-currency | Single currency is default; multi-currency stretch | ☐ Team to confirm |
| 10 | **Deployment target:** Single VM (docker-compose) vs Kubernetes | Single VM is default; k8s stretch | ☐ Team to confirm |
| 11 | **Testcontainers vs embedded** Kafka for integration tests | Testcontainers is default; embedded Kafka (Spring @EmbeddedKafka) as alternative | ☐ Team to confirm |
| 12 | **Contract testing:** Spring Cloud Contract vs Pact | Spring Cloud Contract is default; Pact as alternative | ☐ Team to confirm |

## How to Decide

1. Each team member reads this file before the planning meeting
2. Discuss each item for 2-3 minutes
3. Default: pick the first option if no consensus (keeps us moving)
4. Record decision in `docs/adr/NNN-title.md` with a new ADR number
5. Update this table to mark as decided

## Last Reviewed

2026-09-26 — All items pending team confirmation.