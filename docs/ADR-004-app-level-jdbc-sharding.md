# ADR-004: App-level Region-Based Sharding

**Status:** Accepted
**Date:** 2026-11-09
**Decision Context:** The initial HLD proposal suggested ShardingSphere data-source-level sharding with per-service config (sourced persistence logic). Implementation required a more portable approach that could work with single-database development and avoid caffeine leaks.

**Decision Statement:**
Account rows are sharded at the JDBC application-level using region resolution at the `AccountCommandService`, with enterprise logic injected via a `ShardRouter` bean and database queries constrained to the resolved shard.

**Rationale:**

1. **Single-Database Development**:
   - Enables seamless local development with only PostgreSQL running (docker-compose ensures multi-instance with Valkey caching)

2. **Operational Simplicity**:
   - Eliminates complex ShardingSphere initialization per microservice
   - No need for separate config files per shard
   - Each service stays self-contained

3. **IP-Based Regional Routing**:
   - Customers are routed to regional shards based on registration IP (`49.37.10.10` → APAC, `85.214.132.1` → EU, `8.8.8.8` → AMER)
   - Routing logic buried in `RegionResolver` and `ShardRouter`

4. **Portability**:
   - Can be reconfigured for multi-database deployment
   - Shadow tables (`ledger_events`, `account_core`) remain consistent across shards

5. **Verification via Proof Scripts**:
   - `03-region-sharding.sh` directly queries Postgres snapshots to verify shard assignment

**Consequences:**

- **Positive**:
  - Faster startup; all 8 Java services share a single DB instance (Rwby)
  - Easier debugging (single query to see all data)
  - Eliminates rework required to cleanup ShardingSphere-side config leaks

- **Negative**:
  - Write amplification: all services query the same sharded config table to resolve target shard
  - implicit dependency on `ip_location_db` integration; degrade to fallback region if IP unknown
  - No real-time cross-shard data sync for fraud detection (currently limited to local-account scanning)

**Alternatives Considered:**

1. **ShardingSphere data-source sharding** (initial proposal)
   - Rejected: caused dev-time portability loss when re-running inferences

2. **Per-service Postgres instances** (advanced deployment)
   - Deferred: more complex setup, higher resource requirements

3. **Consistent hash partitioning** (distributed data structures)
   - Rejected: requires persistent contention on account-lock keys (ferretDB lookup vs. regular browsing)

**Implementation:**

- `ShardRouter` bean in `account-command-service` with `hash(String shardKey)` logic
- `RegionResolver` bean with `resolve(String ipAddress)` returning `APAC`/`EU`/`AMER`
- JDBC queries limited to `WHERE region = ?` which takes resolved shard name
- Demo config exposes regionips via proxy headers for in-memory simulation (proof scripts)

**Related:** Proof 3 validation demonstrates rows landing on correct Postgres shard (`ddbs_apac.account_core`, `ddbs_eu.account_core`, `ddbs_amer.account_core`).