# ADR-005: Demo-Only HS256 JWT Auth

**Status:** Accepted, with Production Plan Defer
**Date:** 2026-11-09
**Decision Context:** The HLD spec mentions OAuth2/OIDC, Keycloak integration, and mutual TLS. The initial commit goal was to support *any* secure auth flow; demo documentation of offline token format depend on RSA signing.

**Decision Statement:**
Authentication is limited to demo-grade HS256 HMAC signatures for the initial phase. Production-grade OAuth2/OIDC and mutual TLS are planned for v1.2 deployment (see Production Open Decision #1).

**Rationale:**

1. **Demo Simulation**:
   - HS256 allows immediate artifacting of offline payment tokens via RS256 demo key pair
   - Quick inline fallback for `/api/auth/login` endpoints verified by proof scripts
   - No need to provision external Keycloak instance

2. **Proof Script Feasibility**:
   - Demo scripts (`scripts/demo/*.sh`) independently generate and verify `offline-payment` tokens
   - Syntactic dual-mode: same tokening algorithm can (in theory) be swapped for EdDSA/ES256 once a real key management system is available

3. **Known Limitations**:
   - HS256 is symmetric: every `api-gateway` instance would need to share the same secret, which is insecure
   - No role/permission granularity beyond coarse "customer/bank/ops" guard

**Consequences:**

- **Positive**:
  - Fastest path to understanding the API surface and tokening is verifiable end-to-end
  - No dev-time dependency on external auth providers
  - Code remains complete: JWT signing verification exists and only the signing secret is mocked

- **Negative**:
  - An *indeed* authenticated user flow requires production-scale configuration (OIDC provider, TLS termination, user store)
  - Current auth guard treats the request payload as opaque token and stores session state in memory, which would leak in a horizontal-scaling deployment
  - Payment reservation offline tokens use hardcoded RSA keys (`classpath:/demo-rsa-256.pem`). Production version would need Key Vault / HSM-backed rotation.

4. **Future Work**:
   - v1.2: Migrate to OAuth2/OIDC with Keycloak
   - v1.3: Add request signing + mutual TLS
   - v1.4: Implement granular role/permission scoping

**Alternatives Considered:**

1. **OAuth2/OIDC with Spring Security** (scope: v1.1 or v1.2)
   - Deferred due to dev-time complexity (additional Keycloak port, CORS config, flows)
   - Would have advanced API-first design earlier but at the cost of onboarding

2. **Linting-level security (client trig) + admin CLI** (lower scope)
   - Deferred: would leave demo surface vulnerable to forget-me keys if tokens were persisted

3. **Per-service custom JWT** (current but named differently)
   - Current: same prototype approach but only on gateway; other services deny-auth at startup

**Implementation:**

- `api-gateway/src/main/java/com/bank/gateway/auth/JwtAuthFilter.java` verifies HS256 JWT
- `api-gateway/src/main/java/com/bank/gateway/auth/LoginController.java` responds with HS256 token
- Roles are coarsely enforced: `customer`, `bank`, `ops`
- Offline tokens use RS256 with demo RSA key pair for fraud detection path

**Known Limitations Summary** (prod path):
- Identity verification is not enforced beyond presence of valid token
- User store is in-memory (demo proof only)
- RS256 offline tokens use demo key pair; protocol is production-ready only if the key path is wired
- No multi-factor authentication (MFA)

**Related:** Proof 9 (`offline-payment.sh`) validates RS256 token flow. Production auth decisions deferred to v1.2 promise.

**Related Decisions:**
- Production auth not covered by Scope 0–5 (dev-first), deferred to scope 1.1.