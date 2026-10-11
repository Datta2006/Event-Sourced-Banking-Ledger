package com.bank.payment.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Access to reservations plus balance projections — finders and guarded
 * transitions only, no business logic.
 */
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findByAccountId(UUID accountId);

    List<Reservation> findByStatus(String status);

    List<Reservation> findByAccountIdAndStatus(UUID accountId, String status);

    /** Reservations past their TTL that are still RESERVED — the reaper's input. */
    @Query("select r from Reservation r where r.status = 'RESERVED' and r.expiresAt < :now")
    List<Reservation> findExpiredNow(@Param("now") OffsetDateTime now);

    /**
     * Guarded state transition RESERVED → CAPTURED. Returns 1 when this caller
     * won (first capture), 0 when the reservation was already captured,
     * released, or expired — the application-level double-spend stopper ahead
     * of the partial unique index backstop in the migration.
     */
    @Modifying
    @Query("update Reservation r set r.status = 'CAPTURED', r.capturedAt = :capturedAt " +
           "where r.reservationId = :reservationId and r.status = 'RESERVED'")
    int captureIfReserved(@Param("reservationId") UUID reservationId,
                          @Param("capturedAt") OffsetDateTime capturedAt);
}
