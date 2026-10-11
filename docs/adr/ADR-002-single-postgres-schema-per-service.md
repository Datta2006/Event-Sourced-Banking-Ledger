# ADR-002: Single PostgreSQL instance, schema-per-service (pre-sharding)

- **Status:** Accepted (2026-10-01)
- **Context:** `docs/DEPLOYMENT.md` and the v2 compose stub shipped 3 region-shard
  Postgres instances (`ddbs_apac`/`ddbs_eu`/`ddbs_amer`), but the rebuild brief and
  `HLD_LLD_SystemDesign_v2.md` §9 step 6 explicitly defer the ShardingSphere region
  fragmentation until the schema is stable ("don't do this first — it complicates early
  debugging"). Starting three empty shard databases now would mean every service boots
  against one arbitrary shard while the others stay unused.
- **Decision:** One PostgreSQL 16 instance (`postgres`, db `ledger`, port 5433) with one
  **schema per data-owning service**: `account_command` (ledger_events, account_core,
  account_pii, processed_requests), `loan` (admin_pool, loan_requests), `payment`
  (reservations, processed_requests), `trust_score` (trust_scores). Each service runs
  its own Flyway against its own schema (`spring.flyway.default-schema`, create-schemas
  enabled). Nothing shares a schema or JPA entity across services.
- **Rationale:** Preserves service-ownership boundaries (the constraint that matters)
  while keeping local debugging simple, exactly as §9 step 6 prescribes. Migration to
  the 3-shard physical layout later is a datasource-map change plus moving
  `account_command`'s tables into per-region databases — no application schema changes.
- **Consequences:** The regional-shard demo (v2 §7) is not yet runnable; that is the
  next infrastructure slice, per build order.
- **Related:** `docs/FRAGMENTATION_DESIGN.md` (unchanged as the target design).
