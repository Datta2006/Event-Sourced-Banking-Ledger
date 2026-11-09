package com.bank.payment.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC access to the reservation table (Flyway-managed {@code payment} schema). */
@Repository
public class ReservationRepository {

    public record ReservationRow(
            UUID reservationId,
            UUID accountId,
            BigDecimal amount,
            String status,
            OffsetDateTime expiresAt,
            OffsetDateTime capturedAt,
            String offlineToken,
            UUID merchantAccountId,
            OffsetDateTime createdAt,
            OffsetDateTime voidedAt) {
    }

    private final JdbcTemplate jdbc;
    private final RowMapper<ReservationRow> mapper = (rs, n) -> new ReservationRow(
            rs.getObject("reservation_id", UUID.class),
            rs.getObject("account_id", UUID.class),
            rs.getBigDecimal("amount"),
            rs.getString("status"),
            rs.getObject("expires_at", OffsetDateTime.class),
            rs.getObject("captured_at", OffsetDateTime.class),
            rs.getString("offline_token"),
            rs.getObject("merchant_account_id", UUID.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("voided_at", OffsetDateTime.class));

    public ReservationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(ReservationRow row) {
        jdbc.update("""
                        INSERT INTO payment.reservations
                          (reservation_id, account_id, amount, status, expires_at, offline_token, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                row.reservationId(), row.accountId(), row.amount(), row.status(),
                row.expiresAt(), row.offlineToken(), row.createdAt());
    }

    public Optional<ReservationRow> find(UUID reservationId) {
        return jdbc.query("SELECT * FROM payment.reservations WHERE reservation_id = ?",
                mapper, reservationId).stream().findFirst();
    }

    public List<ReservationRow> list(UUID accountId, String status) {
        if (accountId != null && status != null) {
            return jdbc.query("""
                    SELECT * FROM payment.reservations WHERE account_id = ? AND status = ?
                    ORDER BY created_at DESC
                    """, mapper, accountId, status);
        }
        if (accountId != null) {
            return jdbc.query("""
                    SELECT * FROM payment.reservations WHERE account_id = ?
                    ORDER BY created_at DESC
                    """, mapper, accountId);
        }
        if (status != null) {
            return jdbc.query("""
                    SELECT * FROM payment.reservations WHERE status = ?
                    ORDER BY created_at DESC
                    """, mapper, status);
        }
        return jdbc.query("SELECT * FROM payment.reservations ORDER BY created_at DESC", mapper);
    }

    /** Guarded first-capture-wins transition. Returns 0 when the row was not RESERVED. */
    public int markCaptured(UUID reservationId, UUID merchantAccountId, OffsetDateTime at) {
        return jdbc.update("""
                UPDATE payment.reservations
                   SET status = 'CAPTURED', captured_at = ?, merchant_account_id = ?
                 WHERE reservation_id = ? AND status = 'RESERVED'
                """, at, merchantAccountId, reservationId);
    }

    /** Guarded release/void transition. */
    public int markReleased(UUID reservationId, String status, OffsetDateTime at) {
        return jdbc.update("""
                UPDATE payment.reservations
                   SET status = ?, voided_at = ?
                 WHERE reservation_id = ? AND status = 'RESERVED'
                """, status, at, reservationId);
    }

    /** Force a non-RESERVED status (compensation after a partial capture). */
    public void forceStatus(UUID reservationId, String status, OffsetDateTime at) {
        jdbc.update("UPDATE payment.reservations SET status = ?, voided_at = ? WHERE reservation_id = ?",
                status, at, reservationId);
    }

    public List<ReservationRow> findExpired(OffsetDateTime now, int limit) {
        return jdbc.query("""
                SELECT * FROM payment.reservations
                 WHERE status = 'RESERVED' AND expires_at < ?
                 ORDER BY expires_at
                 LIMIT ?
                """, mapper, now, limit);
    }
}
