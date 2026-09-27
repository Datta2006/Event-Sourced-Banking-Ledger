# Event-Sourced Banking Ledger — v2

**Distributed Systems + DDBS edition.** An event-sourced banking backend whose center of
gravity is *concrete distributed-systems and distributed-database mechanisms you can point
at and prove*: a contended-resource task queue, offline-capable payments, cross-server
concurrency control, and hybrid data fragmentation.

> `HLD_LLD_SystemDesign_v2.md` (repo root) is the **single source of truth** for this
> rebuild and the tiebreaker if any two documents disagree.

---

## Services (8)

| Service | Module | Port | One-liner |
|---|---|---|---|
| API Gateway | `api-gateway` | 8080 | Spring Cloud Gateway + Eureka entry point |
| Account Command Service | `account-command-service` | 8081 | Write side; region-sharded event store via ShardingSphere-JDBC; Kafka + `RFairLock` withdrawal ordering |
| Ledger Query Service | `ledger-query-service` | 8082 | Read model (MongoDB/FerretDB) + replay/recovery from sharded ledger |
| Loan Service | `loan-service` | 8085 | Valkey priority queue + atomic Lua pop-and-debit of the admin pool |
| Payment & Reservation Service | `payment-reservation-service` | 8086 | Online payments + offline payments via signed reservation tokens |
| Trust Score Service | `trust-score-service` | 8087 | 0–100 score + tier per account; publishes `TrustScoreChanged` |
| Fraud Detection Service | `fraud-detection-service` | 8083 | Fraud rules over account/payment events |
| Notification Service | `notification-service` | 8084 | Customer notifications via MailHog |

Shared infrastructure: Eureka (`infra/eureka`), Spring Cloud Config (`infra/config`),
Kafka, Valkey, 3× Postgres region shards, MongoDB/FerretDB. **ShardingSphere is a library
inside Account Command & Ledger Query's JDBC layer, not a service; Valkey is
infrastructure, not a business service.**

## What's distinctive about this project (v2 §1)

| # | Requirement | Mechanism |
|---|---|---|
| 1 | Loan system, pool contention, FCFS + trust score | Redisson `RScoredSortedSet` priority queue (trust tier first, arrival time second) + atomic pool debit via Lua script — two simultaneous ₹2,000 requests against a ₹2,000 pool yield exactly one approval |
| 2 | Internet pay + offline pay via temp reservation | Pessimistic pre-commit reservation (`FundsReserved`/`FundsCaptured`/`FundsReleased`) + Nimbus JOSE+JWT signed offline token the merchant verifies with zero connectivity; replay of the same token is rejected |
| 3 | Multi-server concurrent withdrawal, task queue, FCFS | Kafka partition-per-account (ordering at the broker, not the app server) + Redisson `RFairLock` around the debit |
| 4 | Basic concurrency verification | Optimistic concurrency on the event store (`UNIQUE(aggregate_id, version)`) + idempotency keys on every state-changing call |
| 5 | Hybrid vertical + horizontal fragmentation by IP | Apache ShardingSphere region-based horizontal sharding (registration IP → region → shard) + sensitivity/access-pattern column-split — *both axes, with independent reasons* |
| 6 | Open-source, license-clean tooling | Valkey over Redis, ip-location-db over MaxMind, FerretDB/Postgres-JSONB flagged as the OSI-clean swap for MongoDB |

## Quick start

```bash
./mvnw -DskipTests package     # build all modules (services + infra)
docker compose up -d           # infra + all 8 services
open http://localhost:8761     # Eureka — wait for all services to register
open http://localhost:8025     # MailHog — notification outbox
```

Local demo walkthrough: `docs/DEPLOYMENT.md` §6. API docs: `docs/api_spec/`.

## Documentation map

| Document | Contents |
|---|---|
| `HLD_LLD_SystemDesign_v2.md` | **Source of truth** — full v2 HLD/LLD |
| `docs/ARCHITECTURE.md` | System context, tech stack, service responsibility matrix |
| `docs/EVENT_CATALOG.md` | Every event, topic, producer, consumer |
| `docs/API_SPEC/` | One OpenAPI YAML per service |
| `docs/SAGA_DESIGN.md` | Transfer saga, loan pop-and-debit, withdrawal FCFS sequences |
| `docs/DATA_MODEL.md` | All schemas + ShardingSphere rule config |
| `docs/FRAGMENTATION_DESIGN.md` | Hybrid horizontal + vertical fragmentation deep dive |
| `docs/TESTING_STRATEGY.md` | Test pyramid + the three required concurrency tests |
| `docs/DEPLOYMENT.md` | Compose topology, run order, env vars |
| `docs/OPEN_DECISIONS.md` | Still-open decisions (MongoDB vs FerretDB vs JSONB, …) |

## Status

**Documentation + scaffolding only** — every service module compiles (build files, empty
main classes, `application.yml`) but contains no business logic, controller bodies, or
working queue/lock code yet. Implementation order: `HLD_LLD_SystemDesign_v2.md` §9.
