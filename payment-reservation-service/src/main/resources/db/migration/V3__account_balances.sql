-- Own-schema balance projection for the payment service (ADR-002: one schema
-- per data-owning service). Maintained from account events consumed off Kafka
-- plus this service's own reserved/captured/released transitions, so the
-- reservation logic never joins across schemas at runtime.
CREATE TABLE payment.account_balances (
    account_id         VARCHAR(36) PRIMARY KEY,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    available_balance  NUMERIC(18,2) NOT NULL DEFAULT 0,
    reserved_balance   NUMERIC(18,2) NOT NULL DEFAULT 0,
    version            BIGINT NOT NULL DEFAULT 0,
    updated_at         TIMESTAMP NOT NULL DEFAULT now()
);
