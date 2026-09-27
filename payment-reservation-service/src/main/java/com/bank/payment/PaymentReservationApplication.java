package com.bank.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Payment &amp; Reservation Service (v2).
 *
 * Online path: synchronous call to Account Command Service (MoneyWithdrawn /
 * MoneyDeposited as in v1). Offline path: pessimistic pre-commit reservation —
 * FundsReserved moves amount from available_balance to reserved_balance, mints a
 * Nimbus JOSE+JWT signed offline token; first capture by reservationId wins
 * (FundsCaptured), uninvoked holds expire via FundsReleased
 * (HLD_LLD_SystemDesign_v2.md §4).
 *
 * Scaffolding only — no business logic yet.
 */
@SpringBootApplication
public class PaymentReservationApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentReservationApplication.class, args);
    }
}
