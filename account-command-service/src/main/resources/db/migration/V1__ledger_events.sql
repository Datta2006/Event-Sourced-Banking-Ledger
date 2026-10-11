-- Event store (docs/DATA_MODEL.md §4; v2 §6, §7.2)
-- Append-only log; `region` is the future ShardingSphere horizontal shard key
-- (retrofit deferred per v2 §9 step 6 — single logical table for now).
-- NOTE: aggregate_id/version/event_type/payload carry an explicit NOT NULL beyond
-- the doc table: UNIQUE(aggregate_id, version) must reject NULL versions, otherwise
-- the optimistic-concurrency guard (v2 §6) could be silently bypassed.
CREATE TABLE ledger_events (
  event_id     UUID PRIMARY KEY,
  aggregate_id UUID        NOT NULL,
  version      INT         NOT NULL,
  event_type   VARCHAR     NOT NULL,
  payload      JSONB       NOT NULL,
  region       VARCHAR,
  occurred_at  TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_ledger_events_aggregate_version UNIQUE (aggregate_id, version)
);

CREATE INDEX ix_ledger_events_aggregate_version ON ledger_events (aggregate_id, version ASC);
CREATE INDEX ix_ledger_events_event_type ON ledger_events (event_type);
