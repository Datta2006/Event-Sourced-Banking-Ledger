package com.bank.accountcmd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Account Command Service (v2).
 *
 * Owns the write side of account aggregates: deposits, withdrawals, transfers.
 * Appends events to the region-sharded {@code ledger_events} store through
 * ShardingSphere-JDBC and publishes to Kafka. Withdrawal commands are consumed
 * from a Kafka partition keyed by accountId and guarded by a Redisson
 * {@code RFairLock} per account (HLD_LLD_SystemDesign_v2.md §5, §7).
 *
 * Scaffolding only — no business logic yet.
 */
@SpringBootApplication
public class AccountCommandApplication {
    public static void main(String[] args) {
        SpringApplication.run(AccountCommandApplication.class, args);
    }
}
