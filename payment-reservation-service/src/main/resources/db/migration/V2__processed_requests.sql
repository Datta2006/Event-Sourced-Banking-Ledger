-- Idempotency keys (docs/DATA_MODEL.md §3, v2 §6) — per-service copy (schema payment).
CREATE TABLE processed_requests (
  key               VARCHAR(64) PRIMARY KEY,
  response_snapshot JSONB       NOT NULL,
  expires_at        TIMESTAMPTZ NOT NULL
);
