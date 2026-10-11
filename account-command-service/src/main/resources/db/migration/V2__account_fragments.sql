-- Vertical-fragmentation account fragments (docs/DATA_MODEL.md §4, v2 §7.2).
-- account_core: small, hot, read on almost every request.
CREATE TABLE account_core (
  account_id UUID PRIMARY KEY,
  status     VARCHAR,
  region     VARCHAR,
  opened_at  TIMESTAMPTZ
);

-- account_pii: sensitive, infrequently read, candidate for stricter
-- access control / encryption at rest (v2 §7.2).
CREATE TABLE account_pii (
  account_id      UUID PRIMARY KEY,
  holder_name     VARCHAR,
  kyc_documents   JSONB,
  registration_ip VARCHAR,
  address         TEXT
);
