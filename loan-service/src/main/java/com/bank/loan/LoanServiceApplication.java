package com.bank.loan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Loan Service (v2).
 *
 * Receives loan requests, pushes them onto the Valkey {@code loan:queue}
 * (Redisson RScoredSortedSet, score = (3 - trustTier) * 10^9 + requestedAtEpochMillis),
 * and a worker loop pops the head and runs the atomic Lua debit script against
 * {@code admin_pool:balance}: success → LoanApproved → LoanDisbursed;
 * failure → WAITING_FOR_FUNDS until AdminPoolReplenished
 * (HLD_LLD_SystemDesign_v2.md §3).
 *
 * Scaffolding only — no business logic yet.
 */
@SpringBootApplication
public class LoanServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LoanServiceApplication.class, args);
    }
}
