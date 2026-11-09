-- Runtime fields and support tables added for the completed loan lifecycle.
ALTER TABLE loan_requests ADD COLUMN IF NOT EXISTS purpose VARCHAR(255);
ALTER TABLE loan_requests ADD COLUMN IF NOT EXISTS note TEXT;
ALTER TABLE loan_requests ADD COLUMN IF NOT EXISTS disbursed_at TIMESTAMPTZ;
ALTER TABLE loan_requests ADD COLUMN IF NOT EXISTS due_at TIMESTAMPTZ;
ALTER TABLE loan_requests ADD COLUMN IF NOT EXISTS repaid_at TIMESTAMPTZ;

ALTER TABLE admin_pool ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS loan.processed_requests (
  key               VARCHAR(160) PRIMARY KEY,
  response_snapshot JSONB NOT NULL,
  expires_at        TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS loan.pool_replenishments (
  event_id      UUID PRIMARY KEY,
  amount        NUMERIC(18,2) NOT NULL,
  balance_after NUMERIC(18,2) NOT NULL,
  reason        VARCHAR(32) NOT NULL,
  occurred_at   TIMESTAMPTZ NOT NULL
);

-- Local read-only cache of TrustScoreChanged, so loan requests need no
-- synchronous cross-service call (v2 §3.6).
CREATE TABLE IF NOT EXISTS loan.trust_tiers (
  account_id UUID PRIMARY KEY,
  tier       SMALLINT NOT NULL,
  score      NUMERIC(6,2),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_loan_requests_account ON loan_requests(account_id, requested_at DESC);
CREATE INDEX IF NOT EXISTS ix_loan_requests_status ON loan_requests(status);
