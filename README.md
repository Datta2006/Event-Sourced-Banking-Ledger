# CS465 Event-Sourced Banking Ledger

A distributed event-sourced banking system built for Spring Boot, Kafka, ShardingSphere, FerretDB, and React. This project demonstrates banking primitives—accounts, transfers, loans, trust scoring, fraud detection, and notification—with hybrid fragmentation, CQRS, and saga orchestration.

## 🏗️ Architecture Overview

### Services Layout

```
┌─────────────────────────────────────────────────────────────────┐
│                     API Gateway (8080)                          │
│     • JWT authentication (HS256)                                │
│     • Route to customer/bank/rest ports                         │
│     • Role-based guards                                         │
└─────────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
 Customer                      Bank                    Ops
  Port: 8080                  Port: 8080           Port: 8087
        │                     │                     │
┌─────────────────────────────────────────────────────────────────┐
│                    Event Sourcing Core                          │
├─────────────────────────────────────────────────────────────────┤
│  Account Command (8081)     Transfer Saga (8081)               │
│  • Event append with hash chain                                 │
│  • Account command semantics                                    │
│  • Region routing via JDBC app-level sharding                    │
│  • Idempotent command handling (account-level lock)             │
├─────────────────────────────────────────────────────────────────┤
│  Ledger Query (8082)                                                │
│  • CQRS projection from Kafka → FerretDB                         │
│  • Idempotent event processing                                   │
│  • Read model with version guards                               │
├─────────────────────────────────────────────────────────────────┤
│  Fraud Detection (8083)                                          │
│  • Velocity patterns, risk scoring                              │
│  • Documented `fraud.events` Kafka topic                        │
├─────────────────────────────────────────────────────────────────┤
│  Notification (8084)                                             │
│  • Email via MailHog                                             │
│  • Push to queue → processed asynchronously                     │
└─────────────────────────────────────────────────────────────────┘
        │                     │                     │
        ▼                     ▼                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Business Logic Layer                         │
├─────────────────────────────────────────────────────────────────┤
│  Trust Score (8087)                                             │
│  • Deposit-based scoring (+0.5 per ₹10,000, capped +5/day)      │
│  • Tier boundaries: Basic (0), Trusted (5), Elite (10), King (15)│
│  • Solvable interface for AI reward calculation                  │
├─────────────────────────────────────────────────────────────────┤
│  Loan (8085)                                                    │
│  • Queue-based lending priority pool                            │
│  • Loan queue score: (3 - tier) × 10¹³ + epochMillis            │
│  • Trust-based priority + FCFS within tier                      │
│  • LoanRejected after admin enforcement                        │
├─────────────────────────────────────────────────────────────────┤
│  Payment Reservation (8086)                                     │
│  • Offline payment tokens (RS256)                               │
│  • Token reuse detection (409), expiry (410)                  │
│  • Mundane fraud detection                                     │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                Infrastructure & Observability                   │
├─────────────────────────────────────────────────────────────────┤
│  Eureka Registry (8761)     • Service discovery                  │
│  Config Server (8888)      • Centralized config (native backend) │
│  PostgreSQL (5433)         • ShardingSphere + 3 regional shards │
│    ├─ ddbs_apac            • Asia-Pacific accounts             │
│    ├─ ddbs_eu              • European accounts                 │
│    └─ ddbs_amer            • Americas accounts                 │
│  Kafka (9092)              • Event streaming core               │
│  Valkey (6379)             • Distributed locks, idempotency    │
│  FerretDB (27017)          • CQRS read model (Mongo protocol)  │
│  MailHog (8025)            • Email interception                │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     Customer Portal (3000)                      │
│  • React BrowserRouter (real URLs)                              │
│  • Portals: /customer/* for customers, /bank/* for banks        │
│  • Mock API when VITE_API_BASE not set                         │
└─────────────────────────────────────────────────────────────────┘
```

### Key Features

| Feature | Implementation | Notes |
|---------|---------------|-------|
| Event Sourcing | Kafka events → SQL event store | Hash chain per aggregate (SHA-256) |
| Sharding | JDBC app-level (deviation ADR-004) | IP-based region routing |
| CQRS | Write: Kafka → EventStore, Read: FerretDB | Idempotent projections |
| Consistency | Redisson RFairLock per account | Unique/aggregation version checks |
| Transactions | Saga orchestration for transfers | Compensation on failure |
| Trust Scoring | Solvable interface + tier math | +0.5 per ₹10,000, capped +5 |
| Loan Queue | Queue score formula | (3 - tier) × 10¹³ + timestamp |
| Fraud | Velocity, pattern, scripts | Documented `fraud.events` topic |
| Authentication | HS256 JWT (demo, ADR-005) | Coarse role guard |

## 🚀 Quick Start

### Prerequisites

- **Java 21** (JDK temurin-21 for local dev)
- **Docker** & **Docker Compose**
- **Maven 3.9+** (`./mvnw` wrapper)
- **Node.js 20+** (frontend)
- **jq** (demo proofs)

### 1. Clone the Repository

```bash
git clone <repository-url>
cd ddbs
```

### 2. Verify Docker Images

```bash
# Check if all services build successfully
docker compose up -d --build

# View running services
docker compose ps
```

Expected output: All services marked `Up` and `healthy`.

### 3. Access Services

| Service | URL | Purpose |
|---------|-----|---------|
| API Gateway | http://localhost:8080 | Customer/Bank API entry |
| Eureka Registry | http://localhost:8761 | Service discovery (UI) |
| Config Server | http://localhost:8888 | Backend config (not exposed) |
| FerretDB (Mongo) | mongodb://localhost:27017/ferretdb | CQRS read model |
| PostgreSQL | localhost:5433 | ShardingSphere + regional shards |
| Kafka UI | http://localhost:8025 | MailHog email logs |
| Customer Portal | http://localhost:3000 | React frontend |
| Frontend Dev Server | http://localhost:5173 | React dev mode (parallel) |

### 4. Initialize Database Seeds

The first time you run the system, seeds are created automatically. Pre-seeded demo accounts:

```bash
# Customer: customer/customer123 → Account: 11111111-1111-1111-1111-111111111111
# Bank: bank/bank123
```

### 5. Run Demo Proofs

```bash
# Run all 10 Phase 4 integration proofs
bash scripts/demo/run-all.sh

# Expected: 9+ passes (some timeouts/orphaned events may occur in initial runs)
```

## 📝 Execution Steps

### Development Workflow

#### 1. Start the Stack

```bash
# Build and start all services
docker compose up -d --build

# Wait for all services to be healthy
docker compose ps | grep -E "(healthy|healthy)"
```

#### 2. Run Demo Proofs

```bash
cd scripts/demo

# Run all proofs sequentially
./run-all.sh

# Or run individual proofs
./01-concurrency-idempotency.sh
./02-hash-chain.sh
./03-region-sharding.sh
./04-cqrs-projection.sh
./05-transfer-success.sh
./06-saga-compensation-recovery.sh
./07-loan-priority-pool.sh
./08-loan-repayment.sh
./09-offline-payment.sh
./10-trust-fraud-notifications.sh
```

#### 3. Run Unit Tests

```bash
# Build and run unit tests
./mvnw test -pl trust-score-service,loan-service

# Or run all tests
./mvnw clean test

# Build jar only (skip tests)
./mvnw clean package -DskipTests
```

#### 4. Run Frontend in Dev Mode

```bash
# Start frontend (parallel to backend)
cd frontend
npm install
npm run dev

# Access at http://localhost:5173
```

### CI/CD Pipeline

```bash
# Run full build and tests
./mvnw clean package -DskipTests

# Run health check
./mvnw verify -P health-check
```

## 🔧 Configuration

### Environment Variables

All services read from `src/main/resources/application.yml`. Override with:

```yaml
# docker-compose.yml
environment:
  - SPRING_PROFILES_ACTIVE=dev
  - DB_SHARD_REGION=ddbs_apac
  - VITE_API_BASE=http://localhost:8080
```

### Region-Sharding IPs

| Region | Example IP |
|--------|------------|
| APAC | 49.37.10.10 |
| EU | 85.214.132.1 |
| AMER | 8.8.8.8 |

Demo scripts simulate regions via proxy headers.

## 🧪 Testing

### Unit Tests

```bash
# Trust score service tests
./mvnw test -pl trust-score-service -Dtest=TrustScoreServiceTest

# Loan service tests
./mvnw test -pl loan-service -Dtest=LoanQueueScoreTest
```

### Integration Tests

```bash
# Run all integration proofs
cd scripts/demo
./run-all.sh

# Timeout expected for lagging projections
# May need to retry failed proofs
```

### Behavioral Tests

```bash
# Hash chain integrity
./02-hash-chain.sh

# Concurrency + idempotency
./01-concurrency-idempotency.sh

# Transfer saga
./05-transfer-success.sh
```

## 📚 Documentation

- [EVENT_CATALOG.md](docs/EVENT_CATALOG.md) - Event definitions, topics, schemas
- [DATA_MODEL.md](docs/DATA_MODEL.md) - Relational schema, aggregations, projections
- [API_SPEC](docs/api_spec/) - REST endpoints, request/response contracts
- [ADR-004.md](docs/ADR-004.md) - App-level JDBC sharding deviation
- [ADR-005.md](docs/ADR-005.md) - Demo-only HS256 auth limitations
- [DEPLOYMENT.md](docs/DEPLOYMENT.md) - Production deployment notes
- [OPEN_DECISIONS.md](docs/OPEN_DECISIONS.md) - Outstanding questions, trade-offs

## 🐛 Troubleshooting

### Docker/Compose Issues

```bash
# Stop and remove all containers
docker compose down -v

# Rebuild specific service
docker compose build account-command-service

# View service logs
docker compose logs -f account-command-service

# Restart a single service
docker compose restart account-command-service
```

### Health Check Failures

```bash
# Check Eureka registration
curl http://localhost:8761/eureka/apps/

# Check service health endpoints
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8085/actuator/health
curl http://localhost:8087/actuator/health
```

### Timeout During Proofs

If proofs fail with timeouts:
1. Wait 30-60 seconds for FerretDB projection to catch up
2. Retry the failed proof individually:
   ```bash
   # Re-run failed proof
   ./05-transfer-success.sh
   ```
3. Check Kafka consumer lag:
   ```bash
   kafkacat -L -b localhost:9092 -t account-events
   ```

### Hash Chain Verification Fails

If audit API reports `.valid: false`:
1. Check event version numbering (should be sequential)
2. Verify hash chain algorithm (SHA-256(prevHash + JSON(event)))
3. Ensure no events were skipped or duplicated

```sql
-- Check hash chain integrity
SELECT version, prev_hash, event_id
FROM ddbs_apac.account_events
ORDER BY version
```

## 🚢 Deployment

### Production Setup

```bash
# Build Docker images only
docker compose build

# Push images to registry (example: Docker Hub)
docker tag ddbs-account-command:0.2.0 bank/dbs-account-command:0.2.0
docker push bank/dbs-account-command:0.2.0

# Deploy via Kubernetes/Compose in CI
```

### Environment-Specific Config

1. Set production environment variables in `docker-compose.prod.yml`
2. Use external Config Server instead of native backend
3. Enable production authentication (OAuth2)
4. Rotate RSA keys for offline payment tokens

## 🤝 Contributing

1. Follow existing naming conventions (snake_case in SQL, camelCase in Java)
2. Update `EVENT_CATALOG.md` when adding events
3. Run `./mvnw clean test` before committing
4. Commit in small, meaningful units with clear messages
5. Ensure the repo never remains non-building

## 📄 License

Internal CS465 project. Educational purposes only.

---

## ⚠️ Known Limitations (ADR-005)

- **Demo Auth**: HS256 JWT is for demonstration only. Production requires OAuth2/OIDC.
- **RSA Keys**: Offline payment tokens use demo RSA keys. Production requires Key Vault integration.
- **Idempotency**: Account-level locking is synchronous. Distributed locking overhead applies under heavy load.
- **Loan Rejection**: `LoanRejected` events are pending production enforcement.

## 🔄 Changelog

### Version 0.2.0
- ✅ All 10 Phase 4 integration proofs
- ✅ Unit tests for trust score + loan service
- ✅ Internal idempotency enforcement
- ✅ Hash chain audit per account
- ✅ Region-based sharding (APAC/EU/AMER)
- ✅ CQRS projection with FerretDB
- ✅ Saga orchestration + crash recovery
- 🐛 Config server: switched from Git to native backend for read-only bind mount
- 📦 Dockerfiles upgraded to multi-stage root context builds

### Version 0.1.0
- Initial Spring Boot microservice architecture
- Kafka-based event sourcing foundation
- Basic account and transfer operations