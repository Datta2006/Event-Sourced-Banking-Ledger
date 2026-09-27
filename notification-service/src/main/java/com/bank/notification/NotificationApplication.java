package com.bank.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Notification Service (v2, carried over from v1).
 *
 * Consumes domain events from Kafka (account, loan, payment, trust-score) and
 * sends customer notifications via SMTP — MailHog in dev
 * (HLD_LLD_SystemDesign_v2.md §2, §9 step 7).
 *
 * Scaffolding only — no business logic yet.
 */
@SpringBootApplication
public class NotificationApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
