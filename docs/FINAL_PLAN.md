# Final Plan — completion, verification, demo readiness

> Baseline audited on 2026-11-09. `HLD_LLD_SystemDesign_v2.md` remains the tiebreaker.
> This file records what was actually found, the gap list, and the build order. It is
> updated as items land.

## 0. Baseline (verified, not from the README)

`./mvnw -DskipTests package` **failed** at `account-command-service`: the controller uses
Spring Web annotations but the module POM has no `spring-boot-starter-web`, and the service
uses setters / repository methods that do not exist on the entities. Docker daemon was
down; Maven was absent (wrapper was missing too). The uncommitted diff only contained a
partial API-path rename plus placeholder `OpenAccountCommand` / `TransferStatusView`.

| Service | Baseline status | Notes (what exists) |
|---|---|---|
| Account Command (8081) | **Stub / non-building** | deposit/withdraw/transfer append events; no balance validation, no open/close/KYC REST, no saga state, no audit chain, no idempotency response replay, no sharding |
| Ledger Query (8082) | **Partial, untested** | Mongo repositories + Kafka listener; no replay, no pagination/date filter, no lag/shard surface |
| Loan (8085) | **Partial, untested** | queue + Lua skeleton; only `POST /loans/request`; no list/decision/repay/pool/history, no trust cache, no reconstruction |
| Trust Score (8087) | **Partial, untested** | scoring switch + publish; no idempotency, no change history, no daily cap |
| Payment & Reservation (8086) | **Partial, untested** | local balance table + reservation store; token is a base64 placeholder, no signature verification, no expiry HTTP surface, capture not truly token-verified |
| Fraud (8083) | **Stub** | 2 in-memory rules, list only; no review endpoint, no publish, no new-device/deviation rules |
| Notification (8084) | **Stub** | single-shot send, no idempotency/retries/DLQ, only list |
| API Gateway (8080) | **Partial** | routes + CORS; no auth/roles, no ops aggregate, no resilience filters |
| Frontend | **Implemented against mocks** | complete screen set, hash routing, no login/router; real API path secondary |

## 1. Gap list and build order

1. **Tooling** — add Maven wrapper (done), use Java 21, fix Dockerfiles (multi-stage, repo-root context), add shard databases.
2. **Account Command** — event-sourced core (fold, validate), open/close/KYC, deposit/withdraw/transfer saga with persistence + recovery, idempotency replay, hash chain + verify, correlation IDs, region routing to 3 shard databases, internal credit/debit/reserve/capture/release endpoints.
3. **Ledger Query** — idempotent projections keyed by `lastEventVersion`, statement pagination + date range, replay/verify, Kafka lag + shard row counts, `lastProcessedVersion` surface.
4. **Loan** — priority queue + Lua pop-and-debit, worker, queue/pool/history/decision/repayment endpoints, waiting-list re-check, trust-tier cache, queue reconstruction, overdue default check.
5. **Trust Score** — idempotent consumption, daily deposit cap, change history table + feed, `TrustScoreChanged`.
6. **Payment & Reservation** — real RS256 tokens + public key/JWKS + verify, capture guarded by token + status, expiry release, void, lists.
7. **Fraud / Notification** — full rule set + publish + review; idempotent task-queue consumer, retries, DLQ, MailHog.
8. **Gateway** — method-aware routing, Resilience4j, CORS, JWT login + role guard + account scoping, `/api/ops/*` aggregate.
9. **Frontend** — real URLs + role-guarded customer/bank portals, login, real API as primary, mock fallback preserved.
10. **Tests and demos** — unit + Testcontainers integration where the environment permits; `scripts/demo/*` one PASS/FAIL proof per claim.
11. **Docs and packaging** — README, DEPLOYMENT, DATA_MODEL, OPEN_DECISIONS, API consistency, seed/wait scripts, CI.

## 2. Parallelisable work

Independent after the account/ledger contract is fixed: Loan, Payment, Trust, Fraud,
Notification; frontend screens can proceed against the frozen `docs/api_spec` contract.
Account+ledger+gateway must be sequenced together because routing, auth and event envelopes
are shared.

## 3. Assumptions recorded

- The 3 regional shards are 3 PostgreSQL databases (`ddbs_apac`, `ddbs_eu`, `ddbs_amer`) on
  the compose `postgres` instance. Application-layer routing uses logical table names; see
  `docs/adr/ADR-004-region-shard-routing.md` for why ShardingSphere-JDBC was not embedded
  directly in the JPA/JdbcTemplate slice and the exact transparency guarantee kept.
- Demo account IDs are deterministic UUIDs so the seeded login users can be scoped to them.
- `notification.dlq` is the dead-letter topic; it is created by `kafka-init`.
- A dev-only JWT fallback secret may be used when `JWT_SECRET` is unset; compose always sets it.
