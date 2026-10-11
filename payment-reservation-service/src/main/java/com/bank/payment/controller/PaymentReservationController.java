package com.bank.payment.controller;

import com.bank.payment.model.CaptureFundsCommand;
import com.bank.payment.model.FundsReservedCommand;
import com.bank.payment.model.ReleaseFundsCommand;
import com.bank.payment.persistence.AccountBalance;
import com.bank.payment.persistence.AccountBalanceRepository;
import com.bank.payment.persistence.Reservation;
import com.bank.payment.service.PaymentReservationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for payment reservation operations.
 * Handles HTTP requests and delegates to the PaymentReservationService.
 */
@RestController
@RequestMapping("/reservations")
public class PaymentReservationController {

    @Autowired
    private PaymentReservationService paymentReservationService;

    @Autowired
    private AccountBalanceRepository accountBalanceRepository;

    /** Request body for POST /reservations. */
    public record ReserveBody(
            UUID accountId,
            java.math.BigDecimal amount,
            Integer ttlMinutes,
            String idempotencyKey) {
    }

    /** Request body for POST /reservations/{id}/capture — v2 §4.2. */
    public record CaptureBody(
            UUID accountId,
            String offlineToken,
            UUID merchantAccountId,
            String idempotencyKey) {
    }

    /** Request body for POST /reservations/{id}/release. */
    public record ReleaseBody(
            UUID accountId,
            String reason,
            String idempotencyKey) {
    }

    /** Response view of a reservation — the shape the UI contract expects. */
    public record ReservationView(
            UUID reservationId,
            UUID accountId,
            java.math.BigDecimal amount,
            String status,
            String createdAt,
            String expiresAt,
            String capturedAt,
            String offlineToken) {
    }

    /**
     * Reserve funds for a payment (online or offline).
     */
    @PostMapping
    public ResponseEntity<ReservationView> reserveFunds(@RequestBody ReserveBody body) {
        FundsReservedCommand command = new FundsReservedCommand();
        command.setAccountId(body.accountId());
        command.setAmount(body.amount());
        int ttl = body.ttlMinutes() == null ? 30 : body.ttlMinutes();
        command.setExpiresAt(OffsetDateTime.now().plus(ttl, ChronoUnit.MINUTES));
        command.setIdempotencyKey(body.idempotencyKey());

        Reservation reservation = paymentReservationService.processFundsReservation(command);
        return ResponseEntity.accepted().body(toView(reservation, true));
    }

    /**
     * Capture reserved funds (complete an offline payment). Replaying the
     * same offline token returns 409 — the double-spend proof the demo shows.
     */
    @PostMapping("/{reservationId}/capture")
    public ResponseEntity<?> captureFunds(
            @PathVariable UUID reservationId,
            @RequestBody CaptureBody body) {

        CaptureFundsCommand command = new CaptureFundsCommand();
        command.setReservationId(reservationId);
        command.setAccountId(body.accountId());
        command.setMerchantAccountId(body.merchantAccountId());
        command.setIdempotencyKey(body.idempotencyKey());

        try {
            Reservation reservation = paymentReservationService.processCaptureFunds(command);
            return ResponseEntity.accepted().body(toView(reservation, false));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Release reserved funds (expire or cancel a reservation).
     */
    @PostMapping("/{reservationId}/release")
    public ResponseEntity<?> releaseFunds(
            @PathVariable UUID reservationId,
            @RequestBody ReleaseBody body) {

        ReleaseFundsCommand command = new ReleaseFundsCommand();
        command.setReservationId(reservationId);
        command.setAccountId(body.accountId());
        command.setReason(body.reason() == null ? "MERCHANT_DECLINED" : body.reason());
        command.setIdempotencyKey(body.idempotencyKey());

        try {
            Reservation reservation = paymentReservationService.processReleaseFunds(command);
            return ResponseEntity.accepted().body(toView(reservation, false));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    /** Read model companion: list reservations, optionally filtered. */
    @GetMapping
    public List<ReservationView> listReservations(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) String status) {
        List<Reservation> reservations;
        if (accountId != null && status != null) {
            reservations = paymentReservationService
                    .listByAccountAndStatus(accountId, status);
        } else if (accountId != null) {
            reservations = paymentReservationService.listByAccount(accountId);
        } else if (status != null) {
            reservations = paymentReservationService.listByStatus(status);
        } else {
            reservations = paymentReservationService.listAll();
        }
        return reservations.stream().map(r -> toView(r, false)).toList();
    }

    /** Read model companion: one reservation by id. */
    @GetMapping("/{reservationId}")
    public ResponseEntity<ReservationView> getReservation(@PathVariable UUID reservationId) {
        return paymentReservationService.getReservation(reservationId)
                .map(r -> ResponseEntity.ok(toView(r, false)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Own-schema balance snapshot — the OfflinePayment screen's source of
     * truth for available/reserved before and after reserving.
     */
    @GetMapping("/balances/{accountId}")
    public ResponseEntity<Map<String, Object>> getBalance(@PathVariable UUID accountId) {
        return accountBalanceRepository.findById(accountId.toString())
                .<ResponseEntity<Map<String, Object>>>map(b -> ResponseEntity.ok(Map.of(
                        "accountId", accountId.toString(),
                        "status", b.getStatus(),
                        "availableBalance", b.getAvailableBalance(),
                        "reservedBalance", b.getReservedBalance())))
                .orElse(ResponseEntity.notFound().build());
    }

    private ReservationView toView(Reservation r, boolean withToken) {
        // The offline token is minted once, at reservation time, and never
        // re-issued after capture/release/expiry (v2 §4.2).
        return new ReservationView(
                r.getReservationId(),
                r.getAccountId(),
                r.getAmount(),
                r.getStatus(),
                null,
                r.getExpiresAt() == null ? null : r.getExpiresAt().toString(),
                r.getCapturedAt() == null ? null : r.getCapturedAt().toString(),
                withToken ? PaymentReservationService.mintOfflineToken(r) : null);
    }
}
