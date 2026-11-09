-- Fields required by the completed reservation lifecycle.
ALTER TABLE payment.reservations ADD COLUMN IF NOT EXISTS offline_token TEXT;
ALTER TABLE payment.reservations ADD COLUMN IF NOT EXISTS merchant_account_id UUID;
ALTER TABLE payment.reservations ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE payment.reservations ADD COLUMN IF NOT EXISTS voided_at TIMESTAMPTZ;
ALTER TABLE payment.reservations ALTER COLUMN status TYPE VARCHAR(24);
ALTER TABLE payment.processed_requests ALTER COLUMN key TYPE VARCHAR(160);

CREATE INDEX IF NOT EXISTS ix_reservations_account ON payment.reservations(account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_reservations_status ON payment.reservations(status);
