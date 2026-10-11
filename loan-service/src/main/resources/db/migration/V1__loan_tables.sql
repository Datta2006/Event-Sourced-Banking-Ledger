-- Loan Service tables (docs/DATA_MODEL.md §1, v2 §3.2).
-- Runtime companions (loan:queue RScoredSortedSet, admin_pool:balance Lua-debit
-- mirror) live in Valkey via Redisson — deliberately not SQL (v2 §3.3–3.4).
CREATE TABLE admin_pool (
  pool_id           UUID PRIMARY KEY,
  available_balance NUMERIC(18,2) NOT NULL,
  version           INT NOT NULL          -- optimistic concurrency, mirrors event-store pattern
);

CREATE TABLE loan_requests (
  loan_id       UUID PRIMARY KEY,
  account_id    UUID NOT NULL,
  amount        NUMERIC(18,2) NOT NULL,
  trust_tier    SMALLINT NOT NULL,        -- 0=Low .. 3=Excellent, snapshotted at request time
  requested_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  status        VARCHAR(20) NOT NULL      -- QUEUED, WAITING_FOR_FUNDS, APPROVED, DISBURSED, REJECTED
);
