package com.bank.accountcmd.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 24-hour idempotency store (v2 §6). The stored response snapshot is replayed on a
 * retry with the same {@code Idempotency-Key} instead of re-running the command.
 */
@Component
public class IdempotencyStore {

    private final JdbcTemplate coordinator;

    public IdempotencyStore(JdbcTemplate coordinator) {
        this.coordinator = coordinator;
    }

    public Optional<String> find(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        List<String> rows = coordinator.query("""
                        SELECT response_snapshot::text
                        FROM account_command.processed_requests
                        WHERE key = ? AND expires_at > now()
                        """,
                (rs, n) -> rs.getString(1), key);
        return rows.stream().findFirst();
    }

    public void save(String key, String responseJson) {
        if (key == null || key.isBlank()) {
            return;
        }
        coordinator.update("""
                        INSERT INTO account_command.processed_requests(key, response_snapshot, expires_at)
                        VALUES (?, ?::jsonb, ?)
                        ON CONFLICT (key) DO UPDATE
                          SET response_snapshot = EXCLUDED.response_snapshot,
                              expires_at = EXCLUDED.expires_at
                        """,
                key, responseJson, OffsetDateTime.now().plusHours(24));
    }

    public void purgeExpired() {
        coordinator.update("DELETE FROM account_command.processed_requests WHERE expires_at < now()");
    }
}
