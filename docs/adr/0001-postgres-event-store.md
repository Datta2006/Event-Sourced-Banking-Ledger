# ADR 0001: PostgreSQL Event Store vs EventStoreDB

## Status
Accepted

## Context
We need an append-only event store for the Account Command Service. Options: hand-rolled PostgreSQL `events` table or purpose-built EventStoreDB.

## Decision
Use a hand-rolled PostgreSQL `events` table as the primary event store.

## Consequences

### Positive
- Zero extra infrastructure — only PostgreSQL already needed for accounts/saga state
- Full control over schema, indexing, and query patterns
- Simpler to explain and grade (course project constraint)
- Direct SQL access for audit/administrative views

### Negative
- No built-in projections or subscription mechanisms — we must implement these in Kafka consumers
- No built-in UI for browsing event stream (we'll build a simple admin view if needed)
- Must handle optimistic concurrency and versioning ourselves

## References
- [EventStoreDB](https://www.eventstore.com/)
- PostgreSQL JSONB for flexible payload storage