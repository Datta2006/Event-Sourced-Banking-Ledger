package com.bank.accountcmd.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Durable transfer-saga state (coordinator database). */
@Repository
public class TransferSagaRepository {

    public static final String INITIATED = "INITIATED";
    public static final String SOURCE_DEBITED = "SOURCE_DEBITED";
    public static final String TARGET_CREDITED = "TARGET_CREDITED";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
    public static final String COMPENSATED = "COMPENSATED";

    public record TransferSaga(UUID transferId, UUID fromAccountId, UUID toAccountId,
                               BigDecimal amount, String status, String failureReason,
                               OffsetDateTime startedAt, OffsetDateTime updatedAt) {
        public boolean terminal() {
            return COMPLETED.equals(status) || FAILED.equals(status) || COMPENSATED.equals(status);
        }
    }

    private final JdbcTemplate jdbc;

    public TransferSagaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<TransferSaga> mapper = (rs, n) -> new TransferSaga(
            rs.getObject("transfer_id", UUID.class),
            rs.getObject("from_account_id", UUID.class),
            rs.getObject("to_account_id", UUID.class),
            rs.getBigDecimal("amount"),
            rs.getString("status"),
            rs.getString("failure_reason"),
            rs.getObject("started_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class));

    public void create(UUID transferId, UUID from, UUID to, BigDecimal amount) {
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                        INSERT INTO account_command.transfer_sagas
                          (transfer_id, from_account_id, to_account_id, amount, status, started_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (transfer_id) DO NOTHING
                        """,
                transferId, from, to, amount, INITIATED, now, now);
    }

    public Optional<TransferSaga> find(UUID transferId) {
        return jdbc.query("SELECT * FROM account_command.transfer_sagas WHERE transfer_id = ?",
                mapper, transferId).stream().findFirst();
    }

    public void update(UUID transferId, String status, String failureReason) {
        jdbc.update("""
                        UPDATE account_command.transfer_sagas
                        SET status = ?, failure_reason = ?, updated_at = now()
                        WHERE transfer_id = ?
                        """,
                status, failureReason, transferId);
    }

    public List<TransferSaga> findNonTerminal() {
        return jdbc.query("""
                        SELECT * FROM account_command.transfer_sagas
                        WHERE status IN ('INITIATED','SOURCE_DEBITED','TARGET_CREDITED')
                        ORDER BY started_at
                        """, mapper);
    }

    public List<TransferSaga> recent(int limit) {
        return jdbc.query("""
                        SELECT * FROM account_command.transfer_sagas
                        ORDER BY started_at DESC LIMIT ?
                        """, mapper, limit);
    }
}
