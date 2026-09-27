package com.bank.ledgerquery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ledger Query Service (v2).
 *
 * Consumes account events from Kafka into the read model (MongoDB; the team may
 * swap in FerretDB or Postgres JSONB — see docs/OPEN_DECISIONS.md). Reads
 * ledger_events through ShardingSphere-JDBC for replay and recovery
 * (HLD_LLD_SystemDesign_v2.md §2, §7).
 *
 * Scaffolding only — no business logic yet.
 */
@SpringBootApplication
public class LedgerQueryApplication {
    public static void main(String[] args) {
        SpringApplication.run(LedgerQueryApplication.class, args);
    }
}
