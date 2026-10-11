# ADR-001: Read-model store — FerretDB (MongoDB wire-compatible, Apache-2.0)

- **Status:** Accepted (2026-10-01)
- **Context:** `docs/OPEN_DECISIONS.md` #1 blocked the storage slice: the read model
  (`accounts`, `transactions` collections consumed by Ledger Query Service) could be
  MongoDB Community (SSPL, not OSI-approved), FerretDB (Apache-2.0, MongoDB
  wire-compatible over PostgreSQL + DocumentDB), or plain PostgreSQL `JSONB`
  (`HLD_LLD_SystemDesign_v2.md` §0 flags the license concern and names all three).
- **Decision:** Run **FerretDB 2.7.0** as the read-model store in docker-compose
  (`ferretdb` + `ferretdb-postgres` backend image
  `ghcr.io/ferretdb/postgres-documentdb:17-0.107.0-ferretdb-2.7.0` — the latest
  matched pair published on GHCR; the docs' 2.8.0 pairing is not present there).
  `ledger-query-service` keeps `spring-boot-starter-data-mongodb` unchanged and points
  at `mongodb://ddbs:ddbs@ferretdb:27017/ddbs`.
- **Rationale:** Keeps the stack fully OSI-approved (the stated grading concern) with
  **zero code change** vs. the MongoDB default, and preserves the document-store
  ergonomics that JSONB would lose. MongoDB remains a one-line URI swap
  (`SPRING_DATA_MONGODB_URI`) if the team ever prefers it.
- **Consequences:** One extra container (FerretDB's Postgres backend). FerretDB 2.x is
  slightly less feature-complete than MongoDB 7; the read model only needs basic
  document CRUD/queries, so nothing currently specified is affected.
- **Resolves:** `docs/OPEN_DECISIONS.md` #1.
