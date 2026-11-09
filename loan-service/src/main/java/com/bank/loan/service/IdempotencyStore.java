package com.bank.loan.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 24h idempotency replay store, same semantics as the account service. */
@Component
public class IdempotencyStore {

    private final JdbcTemplate jdbc;

    public IdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> find(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query("""
                        SELECT response_snapshot::text FROM loan.processed_requests
                        WHERE key = ? AND expires_at > now()
                        """, (rs, n) -> rs.getString(1), key)
                .stream().findFirst();
    }

    public void save(String key, String json) {
        if (key == null || key.isBlank()) {
            return;
        }
        jdbc.update("""
                        INSERT INTO loan.processed_requests(key, response_snapshot, expires_at)
                        VALUES (?, ?::jsonb, ?)
                        ON CONFLICT (key) DO UPDATE
                          SET response_snapshot = EXCLUDED.response_snapshot,
                              expires_at = EXCLUDED.expires_at
                        """, key, json, OffsetDateTime.now().plusHours(24));
    }
}
