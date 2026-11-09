-- Runtime tables for the trust-score consumer (docs/DATA_MODEL.md §5, v2 §3.6).

-- Tighten the V1 scaffold: every account starts at 50 (Medium).
UPDATE trust_score.trust_scores SET score = 50 WHERE score IS NULL;
UPDATE trust_score.trust_scores SET tier = 1 WHERE tier IS NULL;
UPDATE trust_score.trust_scores SET last_changed_at = now() WHERE last_changed_at IS NULL;
ALTER TABLE trust_score.trust_scores ALTER COLUMN score SET DEFAULT 50;
ALTER TABLE trust_score.trust_scores ALTER COLUMN score SET NOT NULL;
ALTER TABLE trust_score.trust_scores ALTER COLUMN tier SET DEFAULT 1;
ALTER TABLE trust_score.trust_scores ALTER COLUMN tier SET NOT NULL;
ALTER TABLE trust_score.trust_scores ALTER COLUMN last_changed_at SET NOT NULL;

-- Daily deposit accumulator so "+0.5 per ₹10,000, capped +5/day" is exact even
-- though the exposed score is an integer.
CREATE TABLE IF NOT EXISTS trust_score.deposit_daily (
  account_id      UUID NOT NULL,
  day             DATE NOT NULL,
  total_deposited NUMERIC(18,2) NOT NULL DEFAULT 0,
  points_awarded  INT NOT NULL DEFAULT 0,
  PRIMARY KEY (account_id, day)
);

-- Audit feed behind GET /api/trust-score/changes.
CREATE TABLE IF NOT EXISTS trust_score.trust_score_changes (
  event_id     UUID PRIMARY KEY,
  account_id   UUID NOT NULL,
  score_before INT NOT NULL,
  score_after  INT NOT NULL,
  tier_before  SMALLINT NOT NULL,
  tier_after   SMALLINT NOT NULL,
  reason       VARCHAR(64) NOT NULL,
  changed_at   TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_trust_changes_account
  ON trust_score.trust_score_changes (account_id, changed_at DESC);

-- Consumer dedupe: at-least-once Kafka delivery, exactly-once score effect.
CREATE TABLE IF NOT EXISTS trust_score.processed_events (
  event_id     UUID PRIMARY KEY,
  topic        VARCHAR(64) NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
