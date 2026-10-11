-- Trust Score table (docs/DATA_MODEL.md §5, v2 §3.6).
-- Resolves docs/OPEN_DECISIONS.md #3: Postgres table (rebuildable from events),
-- not Valkey-only.
CREATE TABLE trust_scores (
  account_id      UUID PRIMARY KEY,
  score           INT,                    -- 0–100
  tier            SMALLINT,               -- 0=Low (0–40), 1=Medium (41–70), 2=High (71–90), 3=Excellent (91–100)
  last_changed_at TIMESTAMPTZ             -- last TrustScoreChanged
);
