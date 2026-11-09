package com.bank.payment.controller;

import com.bank.payment.model.PaymentViews.CaptureRequest;
import com.bank.payment.model.PaymentViews.PaymentRequest;
import com.bank.payment.model.PaymentViews.PaymentResult;
import com.bank.payment.model.PaymentViews.ReservationView;
import com.bank.payment.model.PaymentViews.ReserveRequest;
import com.bank.payment.service.OfflineTokenService;
import com.bank.payment.service.PaymentReservationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Payment & reservation endpoints, matching
 * {@code docs/api_spec/payment-reservation-service.yaml}.
 */
@RestController
@RequestMapping("/api/reservations")
public class PaymentReservationController {

    private final PaymentReservationService reservations;
    private final OfflineTokenService tokenService;

    public PaymentReservationController(PaymentReservationService reservations,
                                        OfflineTokenService tokenService) {
        this.reservations = reservations;
        this.tokenService = tokenService;
    }

    /** Create an offline-payment reservation and mint the signed token. */
    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ReservationView create(@RequestBody ReserveRequest request,
                                  @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                  @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return reservations.create(request, idempotencyKey(key), correlation(correlationId, key));
    }

    /** List reservations, newest first; scope by account and/or status. */
    @GetMapping
    public List<ReservationView> list(@RequestParam(required = false) UUID accountId,
                                      @RequestParam(required = false) String status) {
        return reservations.list(accountId, status);
    }

    /** Current reservation state. */
    @GetMapping("/{reservationId}")
    public ReservationView get(@PathVariable UUID reservationId) {
        return reservations.get(reservationId);
    }

    /** Merchant redeems an offline token; replaying the same token returns 409. */
    @PostMapping("/{reservationId}/capture")
    public ReservationView capture(@PathVariable UUID reservationId,
                                   @RequestBody CaptureRequest request,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                   @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return reservations.capture(reservationId, request, idempotencyKey(key),
                correlation(correlationId, key));
    }

    /** Bank action: release an active hold before TTL expiry. */
    @PostMapping("/{reservationId}/void")
    public ReservationView voidReservation(@PathVariable UUID reservationId,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                           @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return reservations.voidReservation(reservationId, idempotencyKey(key),
                correlation(correlationId, key));
    }

    /** Public half of the offline-token key pair (demo verification aid). */
    @GetMapping("/jwks.json")
    public Map<String, Object> jwks() {
        return tokenService.jwks();
    }

    private static String idempotencyKey(String key) {
        return key == null || key.isBlank() ? UUID.randomUUID().toString() : key;
    }

    private static String correlation(String correlationId, String key) {
        return correlationId == null || correlationId.isBlank() ? key : correlationId;
    }
}
