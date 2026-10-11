# Deployment (v2)

> **Source of truth:** `HLD_LLD_SystemDesign_v2.md`. The root `docker-compose.yml` is the
> working local demo: **Valkey** replaces Redis (license, v2 §0) and Kafka runs in KRaft
> mode (no Zookeeper). Per `docs/adr/ADR-002`, the storage slice runs a **single
> Postgres instance with one schema per data-owning service**; the 3-shard region split
> (v2 §7) is deferred until the schema is stable (v2 §9 step 6). The read model is
> **FerretDB** (Apache-2.0, MongoDB wire-compatible — `docs/adr/ADR-001`).

---

## 1. Topology

```mermaid
graph TB
    subgraph Services
        GW[api-gateway :8080]
        CMD[account-command-service :8081]
        QRY[ledger-query-service :8082]
        FRD[fraud-detection-service :8083]
        NTF[notification-service :8084]
        LOAN[loan-service :8085]
        PAY[payment-reservation-service :8086]
        TRUST[trust-score-service :8087]
    end
    subgraph Infrastructure
        EUR[eureka-server :8761]
        CFG[config-server :8888]
        KAFKA[(kafka :9092<br/>KRaft, no zookeeper)]
        VAL[(valkey :6379)]
        PG[(postgres :5433<br/>db ledger — one schema per service)]
        FPG[(ferretdb-postgres<br/>DocumentDB backend)]
        FRDB[(ferretdb :27017<br/>read model)]
        MH[mailhog :8025/:1025]
    end
    CMD --> PG
    LOAN --> PG
    PAY --> PG
    TRUST --> PG
    QRY --> PG
    QRY --> FRDB
    FPG --> FRDB
    LOAN --> VAL
    PAY --> VAL
    CMD --> VAL
    CMD --> KAFKA
    LOAN --> KAFKA
    PAY --> KAFKA
    KAFKA --> QRY & TRUST & FRD & NTF
    NTF --> MH
    GW --> CMD & QRY & LOAN & PAY & TRUST & FRD & NTF
```

## 2. Container inventory

| Container | Image | Host port | Purpose |
|---|---|---|---|
| `eureka-server` | `ddbs-eureka-server:0.2.0` | 8761 | Service discovery |
| `config-server` | `ddbs-config-server:0.2.0` | 8888 | Spring Cloud Config (mounts `./infra/config-repo`) |
| `valkey` | `valkey/valkey:8.1` | 6379 | Redisson backend: `loan:queue` RScoredSortedSet, `admin_pool:balance` Lua debit, `RFairLock` per account |
| `kafka` | `apache/kafka:3.7.2` (KRaft) | 9092 / 29092 | Event streaming + partition-per-account command ordering (official image; bitnami/kafka:3.7 was retired from Docker Hub) |
| `kafka-init` | `apache/kafka:3.7.2` | — | One-shot topic creation for the 5 topics in `EVENT_CATALOG.md` |
| `postgres` | `postgres:16-alpine` | 5433 | db `ledger` — schemas `account_command` / `loan` / `payment` / `trust_score` (ADR-002) |
| `ferretdb` | `ghcr.io/ferretdb/ferretdb:2.7.0` | 27017 | Read model (ADR-001) |
| `ferretdb-postgres` | `ghcr.io/ferretdb/postgres-documentdb:17-0.107.0-ferretdb-2.7.0` | — | FerretDB's DocumentDB storage backend |
| `mailhog` | `mailhog/mailhog` | 8025 / 1025 | Fake SMTP UI for notifications |
| 8 service containers | `ddbs-*:0.2.0` | 8080–8087 | See `docs/ARCHITECTURE.md` §3 |

## 3. Run order

```bash
# build everything first (services + the two infra modules)
mvn -DskipTests package

# start infrastructure, then services (compose handles depends_on ordering)
docker compose up -d

# watch service registration
open http://localhost:8761          # Eureka dashboard
open http://localhost:8025          # MailHog UI (notifications)
```

`depends_on` ordering in the compose file is startup order only — services additionally
tolerate the broker/stores being briefly unavailable at boot (scaffolding note: wiring
readiness probes is implementation-stage work).

## 4. Sharding bootstrap (deferred — see ADR-002)

The storage slice runs a **single Postgres instance** with one Flyway-managed schema per
data-owning service (`account_command`, `loan`, `payment`, `trust_score`); migrations run
automatically at service boot. When the fragmentation retrofit lands (v2 §9 step 6), a
bootstrap script will create the vertical-split schema (`account_core`, `account_pii`,
`ledger_events`) on **each** regional shard and seed ShardingSphere's datasource map
(`ds_apac`, `ds_eu`, `ds_amer`) — deliberately not built now so early debugging stays
simple (v2 §9 step 6: "don't do this first, it complicates early debugging").

## 5. Environment variables (per service)

| Variable | Consumed by | Meaning |
|---|---|---|
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | all | Kafka bootstrap (container-internal `kafka:9092`; host clients use `localhost:29092`) |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | data-owning services | JDBC URL of the single `ledger` database (per-region shard URLs once ShardingSphere lands) |
| `SPRING_DATA_REDIS_HOST`, `REDISSON_ADDRESS` | account-command, loan, payment-reservation | Valkey location for Redisson |
| `SPRING_DATA_MONGODB_URI` | ledger-query | Read-model store — FerretDB (`ferretdb:27017`; MongoDB stays a URI swap away, ADR-001) |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` | notification | MailHog SMTP |
| `EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE` | all | Discovery |

## 6. Local demo scenario (once implemented)

1. Open accounts with distinct registration IPs → rows land in different shards.
2. Seed `admin_pool` = ₹2,000; fire two ₹2,000 loan requests → one `DISBURSED`, one
   `WAITING_FOR_FUNDS` (`docs/TESTING_STRATEGY.md` §3.1).
3. Reserve ₹500 offline, scan the token twice → first capture wins, second rejected
   (`docs/TESTING_STRATEGY.md` §3.3).
4. Two simultaneous withdrawals → FCFS ordering at the broker + fair lock
   (`docs/TESTING_STRATEGY.md` §3.2).

## 7. CI

Testcontainers spins up **Valkey** and **a second Postgres shard** in CI (v2 §0) so the
concurrency tests in `docs/TESTING_STRATEGY.md` run against real infrastructure.
