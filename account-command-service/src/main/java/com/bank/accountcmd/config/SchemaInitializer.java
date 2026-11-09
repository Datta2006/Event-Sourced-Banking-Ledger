package com.bank.accountcmd.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Idempotent DDL for the coordinator schema and all three regional shards.
 * Flyway is not used for the shards because each shard is a physically separate
 * database created at runtime; the statements below are safe to re-run.
 */
@Component
public class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final ShardRouter router;
    private final JdbcTemplate coordinator;

    public SchemaInitializer(ShardRouter router, JdbcTemplate coordinator) {
        this.router = router;
        this.coordinator = coordinator;
    }

    @PostConstruct
    public void init() {
        coordinator.execute("CREATE SCHEMA IF NOT EXISTS account_command");
        coordinator.execute("""
                CREATE TABLE IF NOT EXISTS account_command.processed_requests (
                  key                VARCHAR(128) PRIMARY KEY,
                  response_snapshot  JSONB NOT NULL,
                  expires_at         TIMESTAMPTZ NOT NULL
                )
                """);
        coordinator.execute("""
                CREATE TABLE IF NOT EXISTS account_command.transfer_sagas (
                  transfer_id       UUID PRIMARY KEY,
                  from_account_id   UUID NOT NULL,
                  to_account_id     UUID NOT NULL,
                  amount            NUMERIC(18,2) NOT NULL,
                  status            VARCHAR(24) NOT NULL,
                  failure_reason    TEXT,
                  started_at        TIMESTAMPTZ NOT NULL,
                  updated_at        TIMESTAMPTZ NOT NULL
                )
                """);
        coordinator.execute("""
                CREATE TABLE IF NOT EXISTS account_command.account_directory (
                  account_id  UUID PRIMARY KEY,
                  region      VARCHAR(8) NOT NULL,
                  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
                )
                """);

        for (String region : ShardRouter.REGIONS) {
            JdbcTemplate shard = router.jdbc(region);
            shard.execute("""
                    CREATE TABLE IF NOT EXISTS ledger_events (
                      event_id       UUID PRIMARY KEY,
                      aggregate_id   UUID NOT NULL,
                      version        INT NOT NULL,
                      event_type     VARCHAR(80) NOT NULL,
                      payload        JSONB NOT NULL,
                      region         VARCHAR(8) NOT NULL,
                      correlation_id VARCHAR(128),
                      prev_hash      VARCHAR(64) NOT NULL,
                      hash           VARCHAR(64) NOT NULL,
                      occurred_at    TIMESTAMPTZ NOT NULL,
                      CONSTRAINT uq_ledger_events_aggregate_version UNIQUE (aggregate_id, version)
                    )
                    """);
            // Upgrade a pre-existing (pre-audit-chain) table in place.
            shard.execute("ALTER TABLE ledger_events ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(128)");
            shard.execute("ALTER TABLE ledger_events ADD COLUMN IF NOT EXISTS prev_hash VARCHAR(64)");
            shard.execute("ALTER TABLE ledger_events ADD COLUMN IF NOT EXISTS hash VARCHAR(64)");
            shard.execute("UPDATE ledger_events SET prev_hash = 'GENESIS' WHERE prev_hash IS NULL");
            shard.execute("UPDATE ledger_events SET hash = md5(event_id::text) WHERE hash IS NULL");
            shard.execute("CREATE INDEX IF NOT EXISTS ix_ledger_events_aggregate ON ledger_events(aggregate_id, version)");
            shard.execute("CREATE INDEX IF NOT EXISTS ix_ledger_events_occurred ON ledger_events(occurred_at DESC)");

            shard.execute("""
                    CREATE TABLE IF NOT EXISTS account_core (
                      account_id UUID PRIMARY KEY,
                      status     VARCHAR(16),
                      region     VARCHAR(8),
                      kyc_status VARCHAR(16),
                      opened_at  TIMESTAMPTZ
                    )
                    """);
            shard.execute("ALTER TABLE account_core ADD COLUMN IF NOT EXISTS kyc_status VARCHAR(16)");

            shard.execute("""
                    CREATE TABLE IF NOT EXISTS account_pii (
                      account_id      UUID PRIMARY KEY,
                      holder_name     VARCHAR,
                      kyc_documents   JSONB,
                      registration_ip VARCHAR,
                      address         TEXT
                    )
                    """);
            log.info("Shard {} schema ready", region);
        }
    }
}
