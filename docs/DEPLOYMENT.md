# Deployment Guide

## Local Development

### Prerequisites
- Docker Engine >= 24.0
- Docker Compose V2 (standalone or Docker Desktop)
- Java 17 (or use the dev container)

### Quick Start

```bash
# Clone the repository
git clone <repo-url>
cd dbs

# Build and start all services
docker-compose up --build -d

# Wait for services to be healthy (~10-15s)
# Check status
docker-compose ps

# View logs
docker-compose logs -f

# Test the API Gateway
curl http://localhost:8080/api/accounts/balance?accountId=<some-id>
# Expected: 404 (no accounts yet)

# Open the API Gateway Swagger UI (if enabled)
open http://localhost:8080/swagger-ui.html
```

### Available URLs

| Service | URL | Description |
|---|---|---|
| API Gateway | http://localhost:8080/api | All client traffic |
| Account Command | http://localhost:8081/api | Internal (via gateway) |
| Ledger Query | http://localhost:8082/api | Read queries |
| Fraud Detection | http://localhost:8083/api | Admin/queries |
| Notification | http://localhost:8084/api | Admin/queries |
| Eureka Dashboard | http://localhost:8761 | Service discovery UI |
| Config Server | http://localhost:8888 | Config refresh |
| MailHog (email dev) | http://localhost:8025 | Fake SMTP UI |
| PostgreSQL | localhost:5432 | Event store |
| MongoDB | localhost:27017 | Read models |
| Kafka | localhost:9092 | Event bus |

---

## Cloud / Submission Target

### Recommended: Single VM with Docker Compose

A single Ubuntu VM (2 vCPU, 4GB RAM) running Docker Compose satisfies the rubric's "containerized environment" requirement without needing Kubernetes complexity.

**Justification:**
- Minimal operational overhead for a course project
- All 5 services + infra fit comfortably in 4GB RAM
- Docker Compose is sufficient for demo-day show-and-tell
- Easy to snapshot/backup for grading

**Deployment steps:**
```bash
# On the VM
git clone <repo-url>
cd dbs
docker-compose up -d

# Health check
curl -s http://localhost:8080/actuator/health | jq

# Tail logs for demo
docker-compose logs -f api-gateway
```

### Lightweight Kubernetes (Stretch Goal)

If advanced deployment is desired:

**Target:** k3s (Rancher) or minikube (1 node)

**Manifests in `k8s/`:**
- `namespace.yaml`
- `configmap.yaml` (application.yml overrides)
- `deployment-*.yaml` (one per service)
- `service-*.yaml` (ClusterIPs + NodePort)
- `ingress.yaml` (NGINX Ingress for external access)
- `postgres-statefulset.yaml`
- `mongo-statefulset.yaml`
- `kafka-deployment.yaml` (Strimzi operator recommended)

---

## Environment Variables / Config Strategy

### Approach: Spring Cloud Config Server

All services fetch configuration from `http://config-server:8888` at startup.

**Config structure in `infra/config-repo/`:**
```
application.yml           # Common defaults
account-command-service.yml   # Service-specific
ledger-query-service.yml
fraud-detection-service.yml
notification-service.yml
api-gateway.yml
bootstrap.yml             # Config Server bootstrap
```

**Example `application.yml`:**
```yaml
spring:
  profiles: default
  datasource:
    url: ${SPRING_DATASOURCE_URL}
  kafka:
    bootstrap-servers: ${SPRING_KAFKA_BOOTSTRAP_SERVERS}
```

**Local overrides:** Each developer can create `application-local.yml` for personal settings (not committed).

### Fallback: Docker Compose env vars

All services read `SPRING_APPLICATION_JSON` as a fallback for quick environment overrides.

---

## Health Checks

### Spring Boot Actuator

All services expose Actuator endpoints:
```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/metrics
curl http://localhost:8084/actuator/metrics
```

### Docker Compose healthcheck

```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 30s
```

---

## Logging Aggregation

### Format
JSON structured logging to stdout:
```json
{"timestamp":"2026-09-26T00:00:00Z","level":"INFO","service":"account-command","traceId":"abc123","message":"Event persisted","eventType":"MoneyDeposited","accountId":"uuid-123"}
```

### Console aggregation
`docker-compose logs -f` gives consolidated logs. For more sophisticated aggregation:
- **Stretch:** Fluent Bit → Loki + Grafana (all open-source)

---

## Basic Observability

### Actuator Metrics

| Endpoint | Metric | Meaning |
|---|---|---|
| `/actuator/health` | `status.up` | Service alive |
| `/actuator/metrics/event.count` | Counter | Events produced |
| `/actuator/metrics/kafka.consumer.records` | Gauge | Events consumed |
| `/actuator/scheduledtasks` | List | Background jobs |

### Prometheus (Stretch Goal)

Add `micrometer-registry-prometheus` dependency and endpoint:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

Then scrape from `http://localhost:8081/actuator/prometheus`.

### Grafana Dashboard

Create dashboard panels:
- Event production rate (/s)
- Kafka consumer lag
- Read model rebuild time
- Saga completion rate