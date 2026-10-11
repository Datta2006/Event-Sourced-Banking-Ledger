-- Offline-payment reservations (docs/DATA_MODEL.md §2, v2 §4.3).
-- The partial unique index is the database-level backstop for capture idempotency:
-- at most one CAPTURED row per reservation_id, so a replayed offline token cannot
-- double-spend even if the application-level check races (v2 §4.2 step 5).
CREATE TABLE reservations (
  reservation_id UUID PRIMARY KEY,
  account_id     UUID NOT NULL,
  amount         NUMERIC(18,2) NOT NULL,
  status         VARCHAR(20) NOT NULL,   -- RESERVED, CAPTURED, RELEASED, EXPIRED
  expires_at     TIMESTAMPTZ NOT NULL,
  captured_at    TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_reservation_capture ON reservations (reservation_id) WHERE status = 'CAPTURED';
