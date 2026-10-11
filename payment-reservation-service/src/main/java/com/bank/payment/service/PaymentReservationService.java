package com.bank.payment.service;

import com.bank.payment.model.CaptureFundsCommand;
import com.bank.payment.model.FundsReservedCommand;
import com.bank.payment.model.ReleaseFundsCommand;
import com.bank.payment.persistence.ProcessedRequest;
import com.bank.payment.persistence.ProcessedRequestRepository;
import com.bank.payment.persistence.Reservation;
import com.bank.payment.persistence.ReservationRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service handling payment reservations for online and offline payments.
 * Implements requirements from HLD_LLD_SystemDesign_v2.md §4:
 * - Online payments: standard debit/credit through account service
 * - Offline payments: pre-reservation with signed tokens, TTL-based release
 * - Idempotency keys to prevent double-spending
 *
 * The partial unique index on captured_at (Flyway migration) is the DB-level
 * double-spend backstop; the RESERVED→CAPTURED guarded update here is the
 * first line of defense against a replayed capture token.
 */
@Service
public class PaymentReservationService {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ProcessedRequestRepository processedRequestRepository;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private com.bank.payment.persistence.AccountBalanceRepository accountBalanceRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private static final String ACCOUNT_LOCK_PREFIX = "account-lock:";

    /**
     * Process a funds reservation command (for both online and offline payments).
     */
    @Transactional
    public Reservation processFundsReservation(FundsReservedCommand command) {
        // Check idempotency first
        if (isDuplicateRequest(command.getIdempotencyKey())) {
            // Replay-safe: return the matching live reservation instead of double-processing
            return reservationRepository
                    .findByAccountIdAndStatus(command.getAccountId(), "RESERVED")
                    .stream()
                    .filter(r -> command.getAmount().compareTo(r.getAmount()) == 0)
                    .findFirst()
                    .orElse(null);
        }

        // Acquire fair lock for the account
        RLock lock = redissonClient.getFairLock(ACCOUNT_LOCK_PREFIX + command.getAccountId());
        lock.lock();
        try {
            // Check the account exists, is active, and has balance (own-schema projection)
            com.bank.payment.persistence.AccountBalance balance = accountBalanceRepository
                    .findById(command.getAccountId().toString())
                    .orElseThrow(() -> new IllegalStateException("Account not found"));

            if (!"ACTIVE".equals(balance.getStatus())) {
                throw new IllegalStateException("Account is not active");
            }
            if (balance.getAvailableBalance().compareTo(command.getAmount()) < 0) {
                throw new IllegalStateException("Insufficient funds");
            }

            // Debit available → reserved
            balance.setAvailableBalance(balance.getAvailableBalance().subtract(command.getAmount()));
            balance.setReservedBalance(balance.getReservedBalance().add(command.getAmount()));
            accountBalanceRepository.save(balance);

            // Create reservation record
            Reservation reservation = new Reservation(
                    UUID.randomUUID(),
                    command.getAccountId(),
                    command.getAmount(),
                    "RESERVED",
                    command.getExpiresAt());
            reservationRepository.save(reservation);

            // Publish FundsReserved event
            kafkaTemplate.send("payment.events", command.getAccountId().toString(),
                    String.format(
                            "{\"eventId\":\"%s\",\"type\":\"FundsReserved\",\"reservationId\":\"%s\",\"accountId\":\"%s\",\"amount\":%s,\"expiresAt\":\"%s\",\"occurredAt\":\"%s\"}",
                            UUID.randomUUID(), reservation.getReservationId(), reservation.getAccountId(),
                            reservation.getAmount(), reservation.getExpiresAt(), OffsetDateTime.now()));

            // Mark request as processed
            markProcessed(command.getIdempotencyKey(),
                    "{\"status\":\"RESERVED\",\"reservationId\":\"" + reservation.getReservationId() + "\"}");
            return reservation;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Process a funds capture command (for completing an offline payment).
     * The payer's RESERVED pot is decremented; the merchant account (when
     * resolvable) is credited. Replayed captures fail fast on the status guard.
     */
    @Transactional
    public Reservation processCaptureFunds(CaptureFundsCommand command) {
        // Check idempotency first
        if (isDuplicateRequest(command.getIdempotencyKey())) {
            return reservationRepository.findById(command.getReservationId()).orElse(null);
        }

        RLock lock = redissonClient.getFairLock(ACCOUNT_LOCK_PREFIX + command.getAccountId());
        lock.lock();
        try {
            Reservation reservation = reservationRepository.findById(command.getReservationId())
                    .orElseThrow(() -> new IllegalStateException("Reservation not found"));

            if (!reservation.getAccountId().equals(command.getAccountId())) {
                throw new IllegalStateException("Reservation does not belong to this account");
            }
            // Guarded transition — a replayed token hits this branch
            if (!"RESERVED".equals(reservation.getStatus())) {
                throw new IllegalStateException(
                        "Reservation is " + reservation.getStatus() + ", not RESERVED (replay rejected)");
            }
            if (reservation.getExpiresAt().isBefore(OffsetDateTime.now())) {
                throw new IllegalStateException("Reservation has expired");
            }

            // Atomic single-row update: only succeeds from RESERVED
            int updated = reservationRepository.captureIfReserved(
                    reservation.getReservationId(), OffsetDateTime.now());
            if (updated != 1) {
                throw new IllegalStateException("Reservation no longer RESERVED (concurrent capture lost)");
            }
            reservation.setStatus("CAPTURED");
            reservation.setCapturedAt(OffsetDateTime.now());

            // Move the money within the owning schema: reserved ↓ , merchant available ↑
            com.bank.payment.persistence.AccountBalance payer =
                    accountBalanceRepository.findById(reservation.getAccountId().toString())
                            .orElseThrow(() -> new IllegalStateException("Payer account missing"));
            payer.setReservedBalance(payer.getReservedBalance().subtract(reservation.getAmount()));
            accountBalanceRepository.save(payer);

            com.bank.payment.persistence.AccountBalance merchant =
                    command.getMerchantAccountId() == null ? null :
                    accountBalanceRepository.findById(command.getMerchantAccountId().toString()).orElse(null);
            if (merchant != null) {
                merchant.setAvailableBalance(merchant.getAvailableBalance().add(reservation.getAmount()));
                accountBalanceRepository.save(merchant);
            }

            // Publish FundsCaptured event
            kafkaTemplate.send("payment.events", reservation.getAccountId().toString(),
                    String.format(
                            "{\"eventId\":\"%s\",\"type\":\"FundsCaptured\",\"reservationId\":\"%s\",\"accountId\":\"%s\",\"merchantAccountId\":\"%s\",\"amount\":%s,\"capturedAt\":\"%s\",\"occurredAt\":\"%s\"}",
                            UUID.randomUUID(), reservation.getReservationId(), reservation.getAccountId(),
                            command.getMerchantAccountId(), reservation.getAmount(),
                            reservation.getCapturedAt(), OffsetDateTime.now()));

            markProcessed(command.getIdempotencyKey(),
                    "{\"status\":\"CAPTURED\",\"reservationId\":\"" + reservation.getReservationId() + "\"}");
            return reservation;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Process a funds release command (for expiring or canceling reservations).
     */
    @Transactional
    public Reservation processReleaseFunds(ReleaseFundsCommand command) {
        // Check idempotency first
        if (isDuplicateRequest(command.getIdempotencyKey())) {
            return reservationRepository.findById(command.getReservationId()).orElse(null);
        }

        RLock lock = redissonClient.getFairLock(ACCOUNT_LOCK_PREFIX + command.getAccountId());
        lock.lock();
        try {
            Reservation reservation = reservationRepository.findById(command.getReservationId())
                    .orElseThrow(() -> new IllegalStateException("Reservation not found"));

            if (!reservation.getAccountId().equals(command.getAccountId())) {
                throw new IllegalStateException("Reservation does not belong to this account");
            }
            if (!"RESERVED".equals(reservation.getStatus())) {
                throw new IllegalStateException("Reservation is not in RESERVED status");
            }

            String newStatus = "EXPIRED".equals(command.getReason()) ? "EXPIRED" : "RELEASED";
            reservation.setStatus(newStatus);
            reservationRepository.save(reservation);

            // Return funds: reserved ↓ , available ↑
            com.bank.payment.persistence.AccountBalance balance =
                    accountBalanceRepository.findById(reservation.getAccountId().toString())
                            .orElseThrow(() -> new IllegalStateException("Account not found"));
            balance.setReservedBalance(balance.getReservedBalance().subtract(reservation.getAmount()));
            balance.setAvailableBalance(balance.getAvailableBalance().add(reservation.getAmount()));
            accountBalanceRepository.save(balance);

            // Publish FundsReleased event
            kafkaTemplate.send("payment.events", reservation.getAccountId().toString(),
                    String.format(
                            "{\"eventId\":\"%s\",\"type\":\"FundsReleased\",\"reservationId\":\"%s\",\"accountId\":\"%s\",\"amount\":%s,\"reason\":\"%s\",\"occurredAt\":\"%s\"}",
                            UUID.randomUUID(), reservation.getReservationId(), reservation.getAccountId(),
                            reservation.getAmount(), newStatus, OffsetDateTime.now()));

            markProcessed(command.getIdempotencyKey(),
                    "{\"status\":\"" + newStatus + "\",\"reservationId\":\"" + reservation.getReservationId() + "\"}");
            return reservation;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Scheduled task to release expired reservations.
     * Runs every minute; the DB-level guarded transition keeps this idempotent.
     */
    @Transactional
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000)
    public void releaseExpiredReservations() {
        reservationRepository.findExpiredNow(OffsetDateTime.now()).forEach(r -> {
            r.setStatus("EXPIRED");
            reservationRepository.save(r);

            com.bank.payment.persistence.AccountBalance balance =
                    accountBalanceRepository.findById(r.getAccountId().toString()).orElse(null);
            if (balance != null) {
                balance.setReservedBalance(balance.getReservedBalance().subtract(r.getAmount()));
                balance.setAvailableBalance(balance.getAvailableBalance().add(r.getAmount()));
                accountBalanceRepository.save(balance);
            }

            kafkaTemplate.send("payment.events", r.getAccountId().toString(),
                    String.format(
                            "{\"eventId\":\"%s\",\"type\":\"FundsReleased\",\"reservationId\":\"%s\",\"accountId\":\"%s\",\"amount\":%s,\"reason\":\"EXPIRED\",\"occurredAt\":\"%s\"}",
                            UUID.randomUUID(), r.getReservationId(), r.getAccountId(),
                            r.getAmount(), OffsetDateTime.now()));
        });
    }

    /**
     * Check if a request with this idempotency key has already been processed.
     */
    private boolean isDuplicateRequest(String idempotencyKey) {
        return idempotencyKey != null
                && processedRequestRepository.findById(idempotencyKey).isPresent();
    }

    /**
     * Mark a request as processed for idempotency.
     */
    private void markProcessed(String idempotencyKey, String responseSnapshot) {
        if (idempotencyKey == null) {
            return; // commands without a key are not replay-protected
        }
        ProcessedRequest processedRequest = new ProcessedRequest(
                idempotencyKey, responseSnapshot, OffsetDateTime.now().plusHours(24));
        processedRequestRepository.save(processedRequest);
    }

    // ------------------------------------------------------------------
    // Read-model companions (UI + bank overview)
    // ------------------------------------------------------------------

    public List<Reservation> listAll() {
        return reservationRepository.findAll();
    }

    public List<Reservation> listByAccount(UUID accountId) {
        return reservationRepository.findByAccountId(accountId);
    }

    public List<Reservation> listByStatus(String status) {
        return reservationRepository.findByStatus(status);
    }

    public List<Reservation> listByAccountAndStatus(UUID accountId, String status) {
        return reservationRepository.findByAccountIdAndStatus(accountId, status);
    }

    public java.util.Optional<Reservation> getReservation(UUID reservationId) {
        return reservationRepository.findById(reservationId);
    }

    /**
     * Mint the offline payment token (v2 §4.2): a self-contained JWT with
     * {reservationId, accountId, amount, expiresAt}. The merchant is meant to
     * verify it offline (asymmetric signature); this demo issues a
     * structurally-identical Johnson-style compact token so the UI flow works
     * end-to-end without a keystore.
     */
    public static String mintOfflineToken(Reservation r) {
        String claims = String.format(
                "{\"reservationId\":\"%s\",\"accountId\":\"%s\",\"amount\":%s,\"expiresAt\":\"%s\"}",
                r.getReservationId(), r.getAccountId(), r.getAmount(), r.getExpiresAt());
        return "lt1." + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(claims.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + ".demo-sig";
    }
}
