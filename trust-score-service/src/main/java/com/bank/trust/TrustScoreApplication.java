package com.bank.trust;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Trust Score Service (v2).
 *
 * Consumes account.events and loan.events and maintains a running trust score
 * (0–100) per account: MoneyDeposited +0.5 per ₹10,000 (capped +5/day),
 * LoanRepaidOnTime +10, LoanRepaidEarly +15, LoanTaken −5, LoanRepaidLate −8,
 * LoanDefaulted −20. Tiers: 0–40 Low, 41–70 Medium, 71–90 High, 91–100 Excellent.
 * Publishes TrustScoreChanged for Loan Service's local read-only tier cache
 * (HLD_LLD_SystemDesign_v2.md §3.6).
 *
 * Scaffolding only — no business logic yet.
 */
@SpringBootApplication
public class TrustScoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrustScoreApplication.class, args);
    }
}
