# Open Decisions (v2 rebuild)

> Decisions the team still owes, plus every judgment call made while rebuilding the docs
> and scaffolding from `HLD_LLD_SystemDesign_v2.md` (the tiebreaker for anything below).

---

## 1. Carried forward from v2 §0 — read-model engine

| Option | License | Trade-off |
|---|---|---|
| **MongoDB Community** (current default) | SSPL — ⚠️ *not* OSI-approved | Richest document-store ergonomics; already wired into `ledger-query-service` (`spring-boot-starter-data-mongodb`, compose service) |
| **FerretDB** | Apache 2.0 | MongoDB *wire*-compatible over PostgreSQL — swap is a URI change (`mongodb://ferretdb:27017/ddbs`), zero code change; slightly less feature-complete |
| **PostgreSQL `JSONB`** | PostgreSQL License | One engine fewer to run; loses document-query ergonomics; read model becomes "just more Postgres tables" |

**Flagged in v2 §0:** if a fully open-source stack matters for grading, pick FerretDB or
JSONB. **Needed:** an ADR (the v1 repo used `docs/adr/`; none exist yet in the v2 tree)
recording whichever is chosen.

## 2. Commands topic: `account.commands` vs. publishing into `account.events` (v2 §5.2)

v2 §5.2 says withdrawal commands go to "`account.events` (or a dedicated
`account.commands` topic)". **Judgment call:** the docs assume a dedicated
`account.commands` topic (cleaner command/event separation, `EVENT_CATALOG.md` §6), with
both options noted. Decide at implementation time; nothing else depends on it.

## 3. Trust Score persistence

v2 §3.6 specifies the score, tiers, and `TrustScoreChanged` — but not the store.
**Judgment call:** `docs/DATA_MODEL.md` §5 sketches a `trust_scores` table (Postgres)
behind `trust-score-service`. It could equally be Valkey-only (scores are rebuildable
from events). Decide when Trust Score Service gets implemented (v2 §9 step 2).

## 4. `LoanRepaidEarly` event name

v2 §3.6's trust table lists `LoanRepaidEarly`, but §3.4's prose only names
`LoanRepaidOnTime/Late` in the v2 rebuild brief. **Judgment call:** kept `LoanRepaidEarly`
as a first-class event (per §3.6) alongside `LoanRepaidOnTime`/`LoanRepaidLate`.

## 5. Transfer saga event names

v2 keeps "the v1 transfer saga" without restating its event names, and the v1 repo was
deleted before this rebuild. **Judgment call:** the classic orchestration names in
`EVENT_CATALOG.md` §7 (`TransferInitiated`, `TransferSourceDebited`,
`TransferTargetCredited`, `TransferCompleted`, `TransferFailed`,
`TransferSourceReimbursed`) are treated as the v1 names. If the original v1 doc
resurfaces with different names, v2's tiebreaker rule doesn't apply — this one is a
genuine reconstruction.

## 6. `loan:queue` re-check mechanics after `AdminPoolReplenished`

v2 §3.4: a replenishment "triggers a re-check of the waiting list, highest-priority
first" — but doesn't specify *how* waiting requests are tracked (re-inserted into the
scored set vs. a separate waiting structure replayed in score order). **Judgment call:**
docs describe the behavior, not the mechanism; either implementation satisfies the
contract. `TESTING_STRATEGY.md` §3.1 asserts the observable behavior only.

## 7. Offline token format details

v2 §4.2 fixes the claims (`{reservationId, accountId, amount, expiresAt}`) and asymmetric
signing (Nimbus JOSE+JWT), but not the algorithm (assume EdDSA/ES256-class), key
rotation, or the `offline-token.keystore-path` format. **Judgment call:**
`payment-reservation-service`'s `application.yml` scaffolds a PEM path + issuer; pick
concrete crypto parameters at implementation time.

## 8. Region set & geo-resolution granularity

v2 §7 uses `APAC` / `EU` / `AMER` as examples ("e.g."). **Judgment call:** docs and the
compose file standardize on exactly these three regions for the local demo (2–3 shards
being "enough to prove the concept" per the rebuild brief). A country→region mapping
table and its boundary cases (which continent for IP-less registrations?) are
implementation-stage decisions.

## 9. IP-less / private-IP registrations

The region-resolution flow assumes a resolvable public registration IP. v2 doesn't say
what happens for private/loopback/absent IPs (every local demo request, effectively).
**Judgment call:** scaffolded `AccountSnapshot.region` as required in the API spec, with
the fallback policy (default region? reject?) left open.

## 10. RabbitMQ as the alternative queue

v2 §0 documents RabbitMQ (`x-max-priority`) as the "dedicated broker" alternative for the
loan/withdrawal task queues. **Judgment call:** kept as documentation only — no compose
service, no dependency — since every mechanism in v2 is specified against
Kafka + Redisson/Valkey.

## 11. Rebuild-time housekeeping (no v2 input needed)

- Kept `.gitignore` / `.editorconfig` as-is (sensible; per the rebuild brief).
- `.editorconfig` sets 4-space indent for Java/YAML — followed across the scaffold.
- Port allocation for the three new services (Loan 8085, Payment & Reservation 8086,
  Trust Score 8087) continues the v1 numbering; v2 doesn't assign ports.
- v1's `frontend/` directory and per-service `Dockerfile`s were **deleted** with the v1
  tree and not regenerated: the rebuild brief scopes Step 3 to the 8 service modules, and
  v2 §2 says nothing about a UI. The compose file references `Dockerfile`s per service —
  those are TODOs at implementation time (only `infra/eureka` and `infra/config` ship
  Dockerfiles now, mirroring v1).
- ADRs (`docs/adr/`): v1 had three (Postgres event store, Kafka, orchestration saga).
  All were deleted with the v1 tree. The v2 rebuild brief doesn't list an ADR set, so
  none were recreated; decision #1 above says when to start a fresh ADR log.
