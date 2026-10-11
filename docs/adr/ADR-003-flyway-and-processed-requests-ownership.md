# ADR-003: Flyway for migrations; processed_requests owned per service

- **Status:** Accepted (2026-10-01)
- **Context:** The rebuild brief required choosing Flyway or Liquibase consistently.
  Separately, `docs/DATA_MODEL.md` §3 defines `processed_requests` as used by "every
  state-changing API service" without assigning a physical owner, while the slice
  constraint forbids sharing a schema across services.
- **Decision:**
  1. **Flyway 10** (`flyway-core` + `flyway-database-postgresql` split artifacts) in
     every Postgres-backed service, versioned `db/migration/V*__*.sql` scripts,
     `ddl-auto: validate` so Hibernate can never drift the schema.
  2. **`processed_requests` is duplicated per state-changing service** — an identical
     table in `account_command`, `payment`, and `trust_score` schemas (fraud-detection
     and notification are pure consumers with no persistence this slice). The key is
     global by design (v2 §6); the tables are not.
- **Rationale:** Flyway is the lighter, Spring-Boot-native default and sufficient for
  versioned DDL. Per-service copies are the only way to satisfy both "every
  state-changing service has idempotency storage" and "nothing shares a schema" until
  (if ever) a shared idempotency module with its own schema is justified.
- **Doc impact:** `docs/DATA_MODEL.md` §3 will be annotated with the ownership note so
  the docs do not silently drift from the real schema.
