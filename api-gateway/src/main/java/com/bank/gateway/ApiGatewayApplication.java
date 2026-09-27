package com.bank.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway (v2).
 *
 * Spring Cloud Gateway + Eureka discovery entry point routing to: Account
 * Command, Ledger Query, Loan, Payment &amp; Reservation, Trust Score, Fraud
 * Detection, Notification. Forwards client {@code Idempotency-Key} headers to
 * backing services (HLD_LLD_SystemDesign_v2.md §2, §6).
 *
 * Scaffolding only — no business logic yet.
 */
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
