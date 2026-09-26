# Feature 08: API Gateway & Auth

## User Story

As a user, I want a single entry point to access all banking services so that I don't need to know each service's URL.

## Acceptance Criteria

- [ ] API Gateway (Spring Cloud Gateway) exposes single URL `http://localhost:8080/api`
- [ ] Routes `/api/accounts/**` → Account Command Service
- [ ] Routes `/api/ledger/**` → Ledger Query Service
- [ ] Routes `/api/fraud/**` → Fraud Detection Service
- [ ] Routes `/api/notifications/**` → Notification Service
- [ ] Eureka service discovery integration for dynamic routing
- [ ] Spring Cloud Config Server integration for configuration
- [ ] JWT authentication pass-through (spring security filter)
- [ ] Rate limiting on endpoints (stretch)
- [ ] Request/response logging for audit (stretch)
- [ ] Circuit breaker on downstream services (stretch)

## Services / Events / Endpoints Touched

| Component | Detail |
|---|---|
| API Gateway | Spring Cloud Gateway, route predicates, filters |
| Eureka | Service registration & discovery |
| Config Server | Centralized config fetch |
| Auth | JWT validation at gateway |

## Edge Cases

- Downstream service down → 503 with friendly error
- Invalid JWT → 401 Unauthorized
- Rate limited → 429 Too Many Requests
- Unknown route → 404 Not Found
- Gateway restart → should recover routing from Eureka

## Definition of Done

- [ ] Unit tests: route mapping logic
- [ ] Integration test: gateway routes to live services (Testcontainers)
- [ ] Auth test: invalid JWT → 401
- [ ] Eureka integration verified (service appears in dashboard)
- [ ] OpenAPI spec updated
- [ ] Docs updated
- [ ] Manual: curl via gateway, verify routing works