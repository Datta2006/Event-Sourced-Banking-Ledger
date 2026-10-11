-- Idempotency keys on every state-changing API call (docs/DATA_MODEL.md §3, v2 §6).
-- Owned per-service: identical table in each state-changing service's own schema
-- (schemas are not shared across services). Expired rows purge after 24h.
CREATE TABLE processed_requests (
  key               VARCHAR(64) PRIMARY KEY,
  response_snapshot JSONB       NOT NULL,
  expires_at        TIMESTAMPTZ NOT NULL
);
