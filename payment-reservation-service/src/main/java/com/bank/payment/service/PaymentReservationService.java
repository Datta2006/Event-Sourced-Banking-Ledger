package com.bank.payment.service;

import com.bank.payment.client.AccountClient;
import com.bank.payment.event.PaymentEventPublisher;
import com.bank.payment.model.PaymentViews.CaptureRequest;
import com.bank.payment.model.PaymentViews.PaymentRequest;
import com.bank.payment.model.PaymentViews.PaymentResult;
import com.bank.payment.model.PaymentViews.ReservationView;
import com.bank.payment.model.PaymentViews.ReserveRequest;
import com.bank.payment.service.ReservationRepository.ReservationRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Payment and reservation lifecycle.
 *
 * <p>Balances always live in the account service: reservations call its internal
 * reserve/capture/release endpoints, which append {@code Funds*} events and move
 * available ↔ reserved. This service owns reservation state and the signed offline
 * token; it never keeps a second balance projection that could drift.</p>
 */
@Service
public class PaymentReservationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentReservationService.class);

    private final ReservationRepository repository;
    private final IdempotencyStore idempotency;
    private final RedissonClient redisson;
    private final AccountClient accountClient;
    private final OfflineTokenService tokenService;
    private final PaymentEventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final int defaultTtlMinutes;

    public PaymentReservationService(ReservationRepository repository,
                                     IdempotencyStore idempotency,
                                     RedissonClient redisson,
                                     AccountClient accountClient,
                                     OfflineTokenService tokenService,
                                     PaymentEventPublisher publisher,
                                     ObjectMapper objectMapper,
                                     @Value("${app.offline-token.default-ttl-minutes:30}") int defaultTtlMinutes) {
        this.repository = repository;
        this.idempotency = idempotency;
        this.redisson = redisson;
        this.accountClient = accountClient;
        this.tokenService = tokenService;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.defaultTtlMinutes = defaultTtlMinutes;
    }

    // ------------------------------------------------------------------ reservations

    public ReservationView create(ReserveRequest request, String idempotencyKey,
                                  String correlationId) {
        Optional<ReservationView> replay = replay(idempotencyKey);
        if (replay.isPresent()) {
            return replay.get();
        }
        if (request.accountId() == null) {
            throw new PaymentExceptions.BadRequest("accountId is required");
        }
        validateAmount(request.amount());
        int ttl = request.ttlMinutes() == null || request.ttlMinutes() <= 0
                ? defaultTtlMinutes : request.ttlMinutes();
        UUID reservationId = deterministicId("reservation", idempotencyKey);
        Optional<ReservationRow> existing = repository.find(reservationId);
        if (existing.isPresent()) {
            return view(existing.get(), true);
        }
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusMinutes(ttl);

        try {
            accountClient.reserve(request.accountId(), reservationId, request.amount(), correlationId);
        } catch (AccountClient.AccountCallException e) {
            throw mapAccountFailure(e, "Reserve failed");
        }

        String token = tokenService.mint(reservationId, request.accountId(), request.amount(), expiresAt);
        ReservationRow row = new ReservationRow(reservationId, request.accountId(), request.amount(),
                "RESERVED", expiresAt, null, token, null, now, null);
        try {
            repository.insert(row);
        } catch (RuntimeException e) {
            // Compensate the hold if the reservation row could not be stored.
            try {
                accountClient.release(request.accountId(), reservationId, request.amount(),
                        "RESERVATION_WRITE_FAILED", correlationId);
            } catch (RuntimeException compensation) {
                log.error("Could not compensate reservation {}: {}",
                        reservationId, compensation.getMessage());
            }
            throw e;
        }

        publisher.publish(request.accountId(), "FundsReserved",
                payload("reservationId", reservationId, "accountId", request.accountId(),
                        "amount", request.amount(), "expiresAt", expiresAt.toString()),
                correlationId);
        ReservationView view = view(row, true);
        remember(idempotencyKey, view);
        return view;
    }

    public List<ReservationView> list(UUID accountId, String status) {
        return repository.list(accountId, status == null || status.isBlank() ? null : status)
                .stream().map(row -> view(row, false)).toList();
    }

    public ReservationView get(UUID reservationId) {
        return view(find(reservationId), false);
    }

    public ReservationView capture(UUID reservationId, CaptureRequest request,
                                   String idempotencyKey, String correlationId) {
        Optional<ReservationView> replay = replay(idempotencyKey);
        if (replay.isPresent()) {
            return replay.get();
        }
        ReservationView result = withLock(reservationId, () -> {
            ReservationRow row = find(reservationId);
            if (!"RESERVED".equals(row.status())) {
                throw new PaymentExceptions.Conflict(
                        "Reservation is " + row.status() + " (replayed token rejected)");
            }
            if (request.offlineToken() == null || request.offlineToken().isBlank()) {
                throw new PaymentExceptions.BadRequest("offlineToken is required");
            }
            OfflineTokenService.Claims claims;
            try {
                claims = tokenService.verify(request.offlineToken());
            } catch (OfflineTokenService.InvalidTokenException e) {
                throw new PaymentExceptions.BadRequest(e.getMessage());
            }
            if (!reservationId.toString().equals(claims.reservationId())
                    || !row.accountId().equals(claims.accountId())
                    || row.amount().compareTo(claims.amount()) != 0) {
                throw new PaymentExceptions.BadRequest("Token does not match this reservation");
            }
            OffsetDateTime now = OffsetDateTime.now();
            if (now.isAfter(row.expiresAt())) {
                expire(row, correlationId);
                throw new PaymentExceptions.Gone("Reservation has expired");
            }

            try {
                accountClient.capture(row.accountId(), reservationId, row.amount(), correlationId);
            } catch (AccountClient.AccountCallException e) {
                throw mapAccountFailure(e, "Capture failed");
            }

            UUID merchantId = request.merchantAccountId() != null
                    ? request.merchantAccountId() : row.merchantAccountId();
            if (merchantId != null) {
                try {
                    accountClient.credit(merchantId, row.amount(),
                            "PAYMENT_CAPTURE:" + reservationId, correlationId);
                } catch (AccountClient.AccountCallException e) {
                    // The payer hold is gone but the merchant could not be paid:
                    // return the money to the payer and mark the reservation released.
                    try {
                        accountClient.credit(row.accountId(), row.amount(),
                                "PAYMENT_CAPTURE_REVERSAL:" + reservationId, correlationId);
                    } catch (RuntimeException compensation) {
                        log.error("Capture reversal for {} failed: {}",
                                reservationId, compensation.getMessage());
                    }
                    repository.forceStatus(reservationId, "RELEASED", OffsetDateTime.now());
                    publisher.publish(row.accountId(), "FundsReleased",
                            payload("reservationId", reservationId, "accountId", row.accountId(),
                                    "amount", row.amount(), "reason", "MERCHANT_CREDIT_FAILED"),
                            correlationId);
                    throw mapAccountFailure(e, "Merchant credit failed");
                }
            }

            int updated = repository.markCaptured(reservationId, merchantId, now);
            if (updated != 1) {
                throw new PaymentExceptions.Conflict("Concurrent capture lost the race");
            }
            publisher.publish(row.accountId(), "FundsCaptured",
                    payload("reservationId", reservationId, "accountId", row.accountId(),
                            "merchantAccountId", merchantId, "amount", row.amount(),
                            "capturedAt", now.toString()), correlationId);
            return view(find(reservationId), false);
        });
        remember(idempotencyKey, result);
        return result;
    }

    public ReservationView voidReservation(UUID reservationId, String idempotencyKey,
                                           String correlationId) {
        Optional<ReservationView> replay = replay(idempotencyKey);
        if (replay.isPresent()) {
            return replay.get();
        }
        ReservationView result = withLock(reservationId, () -> {
            ReservationRow row = find(reservationId);
            if (!"RESERVED".equals(row.status())) {
                throw new PaymentExceptions.Conflict("Reservation is " + row.status());
            }
            release(row, "RELEASED", "MANUAL_VOID", correlationId);
            return view(find(reservationId), false);
        });
        remember(idempotencyKey, result);
        return result;
    }

    // ------------------------------------------------------------------ online payment

    public PaymentResult payOnline(PaymentRequest request, String idempotencyKey,
                                   String correlationId) {
        Optional<PaymentResult> replay = replayPayment(idempotencyKey);
        if (replay.isPresent()) {
            return replay.get();
        }
        if (request.fromAccountId() == null || request.toAccountId() == null) {
            throw new PaymentExceptions.BadRequest("fromAccountId and toAccountId are required");
        }
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new PaymentExceptions.BadRequest("Cannot pay the same account");
        }
        validateAmount(request.amount());
        UUID paymentId = deterministicId("payment", idempotencyKey);
        try {
            accountClient.debit(request.fromAccountId(), request.amount(),
                    "ONLINE_PAYMENT_OUT:" + paymentId, correlationId);
        } catch (AccountClient.AccountCallException e) {
            throw mapAccountFailure(e, "Payment debit failed");
        }
        try {
            accountClient.credit(request.toAccountId(), request.amount(),
                    "ONLINE_PAYMENT_IN:" + paymentId, correlationId);
        } catch (AccountClient.AccountCallException e) {
            try {
                accountClient.credit(request.fromAccountId(), request.amount(),
                        "ONLINE_PAYMENT_REVERSAL:" + paymentId, correlationId);
            } catch (RuntimeException compensation) {
                log.error("Online payment reversal {} failed: {}", paymentId, compensation.getMessage());
            }
            throw mapAccountFailure(e, "Payment credit failed and was reversed");
        }
        PaymentResult result = new PaymentResult(paymentId, "COMPLETED");
        rememberPayment(idempotencyKey, result);
        return result;
    }

    // ------------------------------------------------------------------ expiry

    @Scheduled(fixedDelayString = "${app.reservation.expiry-delay-ms:30000}", initialDelay = 10000)
    public void expireReservations() {
        for (ReservationRow row : repository.findExpired(OffsetDateTime.now(), 50)) {
            try {
                withLock(row.reservationId(), () -> {
                    ReservationRow current = find(row.reservationId());
                    if ("RESERVED".equals(current.status())) {
                        expire(current, "expiry-job");
                    }
                    return null;
                });
            } catch (RuntimeException e) {
                log.warn("Could not expire reservation {}: {}",
                        row.reservationId(), e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private void expire(ReservationRow row, String correlationId) {
        try {
            accountClient.release(row.accountId(), row.reservationId(), row.amount(),
                    "EXPIRED", correlationId);
        } catch (AccountClient.AccountCallException e) {
            throw mapAccountFailure(e, "Release failed");
        }
        int updated = repository.markReleased(row.reservationId(), "EXPIRED", OffsetDateTime.now());
        if (updated == 1) {
            publisher.publish(row.accountId(), "FundsReleased",
                    payload("reservationId", row.reservationId(), "accountId", row.accountId(),
                            "amount", row.amount(), "reason", "EXPIRED"), correlationId);
        }
    }

    private void release(ReservationRow row, String status, String reason, String correlationId) {
        try {
            accountClient.release(row.accountId(), row.reservationId(), row.amount(),
                    reason, correlationId);
        } catch (AccountClient.AccountCallException e) {
            throw mapAccountFailure(e, "Release failed");
        }
        int updated = repository.markReleased(row.reservationId(), status, OffsetDateTime.now());
        if (updated != 1) {
            throw new PaymentExceptions.Conflict("Reservation is no longer RESERVED");
        }
        publisher.publish(row.accountId(), "FundsReleased",
                payload("reservationId", row.reservationId(), "accountId", row.accountId(),
                        "amount", row.amount(), "reason", reason), correlationId);
    }

    private ReservationRow find(UUID reservationId) {
        return repository.find(reservationId)
                .orElseThrow(() -> new PaymentExceptions.NotFound(
                        "Reservation not found: " + reservationId));
    }

    private ReservationView view(ReservationRow row, boolean includeToken) {
        return new ReservationView(row.reservationId(), row.accountId(), row.amount(),
                row.status(), row.expiresAt(), row.capturedAt(),
                includeToken ? row.offlineToken() : null);
    }

    private RuntimeException mapAccountFailure(AccountClient.AccountCallException e, String prefix) {
        if (e.status() == 409) {
            return new PaymentExceptions.Conflict(prefix + ": " + e.getMessage());
        }
        if (e.status() == 404) {
            return new PaymentExceptions.NotFound(prefix + ": " + e.getMessage());
        }
        if (e.status() == 400) {
            return new PaymentExceptions.BadRequest(prefix + ": " + e.getMessage());
        }
        return new PaymentExceptions.Upstream(prefix + ": " + e.getMessage());
    }

    private ReservationView withLock(UUID reservationId, java.util.function.Supplier<ReservationView> work) {
        RLock lock = redisson.getFairLock("reservation-lock:" + reservationId);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(10, 30, TimeUnit.SECONDS);
            if (!acquired) {
                throw new PaymentExceptions.Conflict("Could not lock reservation " + reservationId);
            }
            return work.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentExceptions.Conflict("Interrupted while locking reservation");
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new PaymentExceptions.BadRequest("amount must be greater than zero");
        }
        if (amount.scale() > 2) {
            throw new PaymentExceptions.BadRequest("amount supports at most 2 decimal places");
        }
    }

    private static UUID deterministicId(String namespace, String key) {
        if (key == null || key.isBlank()) {
            return UUID.randomUUID();
        }
        return UUID.nameUUIDFromBytes((namespace + ":" + key).getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> payload(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    private Optional<ReservationView> replay(String key) {
        return idempotency.find(key).map(json -> {
            try {
                return objectMapper.readValue(json, ReservationView.class);
            } catch (Exception e) {
                throw new IllegalStateException("Cannot replay reservation response", e);
            }
        });
    }

    private Optional<PaymentResult> replayPayment(String key) {
        return idempotency.find(key).map(json -> {
            try {
                return objectMapper.readValue(json, PaymentResult.class);
            } catch (Exception e) {
                throw new IllegalStateException("Cannot replay payment response", e);
            }
        });
    }

    private void remember(String key, ReservationView view) {
        try {
            idempotency.save(key, objectMapper.writeValueAsString(view));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot store idempotent reservation response", e);
        }
    }

    private void rememberPayment(String key, PaymentResult result) {
        try {
            idempotency.save(key, objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot store idempotent payment response", e);
        }
    }

    /** Payment-specific failures. */
    public static final class PaymentExceptions {
        private PaymentExceptions() {
        }

        public static class NotFound extends RuntimeException {
            public NotFound(String message) { super(message); }
        }

        public static class Conflict extends RuntimeException {
            public Conflict(String message) { super(message); }
        }

        public static class BadRequest extends RuntimeException {
            public BadRequest(String message) { super(message); }
        }

        public static class Upstream extends RuntimeException {
            public Upstream(String message) { super(message); }
        }

        public static class Gone extends RuntimeException {
            public Gone(String message) { super(message); }
        }
    }
}
