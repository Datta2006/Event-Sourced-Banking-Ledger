# Open-Source Libraries & APIs to Reuse

> These are **optional accelerators**. The course rubric is satisfied without any of them, but they reduce reinvented boilerplate and let the team spend more time on the event-sourcing/CQRS/saga core (the actual grading focus).

---

## 1. Event Sourcing / CQRS Frameworks (Java)

| Option | License | Why / Where |
|---|---|---|
| **Axon Framework + Axon Server (Community)** | Apache 2.0 | Full event-store + command-bus + saga framework. [GitHub](https://github.com/AxonFramework/AxonFramework) |
| **Hand-rolled Postgres event store** | Our own | Full control, easier grading, no extra infra service |
| **Eventuate Tram** | Apache 2.0 | Another option for event sourcing in Java |

**Recommendation:** Hand-rolled Postgres event store. Axon is powerful but adds complexity that obscures the concepts we're learning for the course. The hand-rolled approach gives us complete visibility into every line of event logic, which is what the graders likely look for.

### Reference Implementations to Study
- [kbastani/event-sourcing-microservices-example](https://github.com/kbastani/event-sourcing-microservices-example) — Spring Boot + Kafka
- [Slimani-CE/compte-cqrs-event-sourcing](https://github.com/Slimani-CE/compte-cqrs-event-sourcing) — Axon-based bank CQRS
- [robinhosz/cqrs-eventsourcing-springboot](https://github.com/robinhosz/cqrs-eventsourcing-springboot) — Kafka + MySQL/MongoDB bank
- [adityagarde/spring-bank-microservices-app](https://github.com/adityagarde/spring-bank-microservices-app) — Kafka bank demo
- [fuinorg/ddd-cqrs-4-java-example](https://github.com/fuinorg/ddd-cqrs-4-java-example) — EventStoreDB-based

---

## 2. Dedicated Event Store

| Option | License | Why / Where |
|---|---|---|
| **EventStoreDB** | GPL-3.0 / Commercial | Purpose-built append-only store with built-in subscriptions, projections, and stream versioning |
| **Hand-rolled Postgres `events` table** | Our own | Zero extra moving parts, easier to demo |

**Trade-off:** EventStoreDB gives built-in replay, versioning, and projections out of the box, but adds a 6th container to docker-compose and a service to manage. For a course demo with 4-5 containers already, simplicity wins. **We choose Postgres.**

---

## 3. CDC / Outbox Pattern

| Option | License | Why / Where |
|---|---|---|
| **Debezium** | Apache 2.0 | CDC connector publishes DB changes as Kafka topics |
| **Transactional Outbox** (hand-rolled) | Our own | Write event + outbox row in same DB transaction; Debezium polls outbox |

**Recommendation:** Hand-rolled transactional outbox (simpler for course scope). Debezium is a stretch goal if we want to demonstrate CDC.

---

## 4. Messaging Broker

| Option | License | Why / Where |
|---|---|---|
| **Apache Kafka** | Apache 2.0 | Strong ordering per partition, native replay, wide ecosystem |
| **RabbitMQ** | MPL 2.0 | Simpler setup, good for point-to-point |

**Recommendation:** Apache Kafka. Nearly every reference CQRS banking demo uses it. Kafka's partition-based ordering directly maps to our aggregate versioning needs. RabbitMQ would be simpler but lacks built-in event replay, which we need for the disaster recovery feature.

---

## 5. Fraud Detection Data / Logic

| Option | License | Why / Where |
|---|---|---|
| **Kaggle Credit Card Fraud Detection** | CC BY 4.0 | ~284,807 transactions, 492 fraud labels from European cardholders |
| **PaySim** | Open source | Synthetic mobile-money transactions (~7M records) |
| **Rule-based detection** | Our own | Velocity checks, threshold checks, geo/time anomalies |
| **ML model (stretch)** | Various | Train offline on Kaggle/PaySim, serve via lightweight endpoint |

**Recommendation:** Rule-based fraud detection (velocity + threshold + anomaly) as primary path. ML is a stretch goal if time allows. Use Kaggle dataset for test data generation and rule validation. [Kaggle Dataset](https://www.kaggle.com/datasets/mlg-ulb/creditcardfraud)

---

## 6. Notifications

| Option | License | Why / Where |
|---|---|---|
| **Novu** | MIT | Unified email/SMS/push/in-app API, Docker Compose self-host |
| **MailHog** | MIT | Fake SMTP server with web UI for dev |
| **Mailpit** | MIT | Modern alternative to MailHog |

**Recommendation:** MailHog for local dev (simple, lightweight). Novu is a stretch goal if we want a production-grade notification infra.

---

## 7. Currency / FX Rates

| Option | License | Why / Where |
|---|---|---|
| **Frankfurter** | Open source (ECB) | Free, no API key, self-hostable via Docker |
| **Open Exchange Rates** | Commercial | Requires API key |

**Recommendation:** Frankfurter API if multi-currency transfers become a stretch goal. [Frankfurter API](https://www.frankfurter.app/) | [Docker Image](https://github.com/bepo/frankfurter)

---

## 8. IBAN / Account Validation

| Option | License | Why / Where |
|---|---|---|
| **IBAN4j** | Apache 2.0 | Java library for IBAN checksum validation |
| **Public IBAN API** | Free tier | Network call (demo-day risk) |

**Recommendation:** IBAN4j library — no network dependency, works offline for demos.

---

## 9. Auth / Identity

| Option | License | Why / Where |
|---|---|---|
| **Spring Security + JWT** | Apache 2.0 | Simple, first-party Spring, no extra infra |
| **Keycloak** | Apache 2.0 | Full IAM, OAuth2/OIDC, self-hostable |

**Recommendation:** Spring Security + JWT at gateway. Keycloak is a stretch goal if the rubric requires "proper authentication."

---

## 10. Observability

| Option | License | Why / Where |
|---|---|---|
| **Spring Boot Actuator + Micrometer** | Apache 2.0 | Health checks, metrics out of the box |
| **Prometheus + Grafana** | Apache 2.0 | Metrics dashboard |
| **Loki + Grafana** | AGPL 3.0 | Log aggregation |

**Recommendation:** Actuator + Micrometer baseline. Prometheus + Grafana stretch goal for dashboard. Loki stretch goal for log aggregation.

---

## Summary: Recommended Stack per Category

| Category | Primary Choice | Stretch Goal |
|---|---|---|
| Event Store | Hand-rolled Postgres | EventStoreDB |
| Message Broker | Apache Kafka | — (already chosen) |
| Fraud | Rule-based + Kaggle test data | ML model |
| Notifications | MailHog | Novu |
| Auth | Spring Security + JWT | Keycloak |
| FX | Frankfurter API | — |
| IBAN Validation | IBAN4j | — |
| Observability | Actuator + Micrometer | Prometheus + Grafana + Loki |
| CDC | Hand-rolled outbox | Debezium |
