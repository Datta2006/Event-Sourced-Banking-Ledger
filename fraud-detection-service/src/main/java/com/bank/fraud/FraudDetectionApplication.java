package com.bank.fraud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Fraud Detection Service (v2, carried over from v1).
 *
 * Consumes account and payment events from Kafka and evaluates fraud rules
 * (HLD_LLD_SystemDesign_v2.md §2, §9 step 7).
 *
 * Scaffolding only — no business logic yet.
 */
@SpringBootApplication
public class FraudDetectionApplication {
    public static void main(String[] args) {
        SpringApplication.run(FraudDetectionApplication.class, args);
    }
}
