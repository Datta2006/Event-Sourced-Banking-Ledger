package com.bank.loan.service;

import com.bank.loan.model.LoanViews.LoanRequestView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC persistence for loan requests and the durable admin-pool mirror. */
@Repository
public class LoanRepository {

    public record LoanRow(UUID loanId, UUID accountId, BigDecimal amount, String purpose,
                          int trustTier, String status, OffsetDateTime requestedAt,
                          OffsetDateTime disbursedAt, OffsetDateTime dueAt,
                          OffsetDateTime repaidAt, String note) {
        public LoanRequestView view() {
            return new LoanRequestView(loanId, accountId, amount, purpose, trustTier, status,
                    requestedAt, disbursedAt, dueAt, repaidAt, note);
        }
    }

    private final JdbcTemplate jdbc;
    private final RowMapper<LoanRow> mapper = (rs, n) -> new LoanRow(
            rs.getObject("loan_id", UUID.class),
            rs.getObject("account_id", UUID.class),
            rs.getBigDecimal("amount"),
            rs.getString("purpose"),
            rs.getInt("trust_tier"),
            rs.getString("status"),
            rs.getObject("requested_at", OffsetDateTime.class),
            rs.getObject("disbursed_at", OffsetDateTime.class),
            rs.getObject("due_at", OffsetDateTime.class),
            rs.getObject("repaid_at", OffsetDateTime.class),
            rs.getString("note"));

    public LoanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(LoanRow row) {
        jdbc.update("""
                        INSERT INTO loan.loan_requests
                          (loan_id, account_id, amount, purpose, trust_tier, requested_at, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                row.loanId(), row.accountId(), row.amount(), row.purpose(),
                row.trustTier(), row.requestedAt(), row.status());
    }

    public Optional<LoanRow> find(UUID loanId) {
        return jdbc.query("SELECT * FROM loan.loan_requests WHERE loan_id = ?", mapper, loanId)
                .stream().findFirst();
    }

    public List<LoanRow> list(UUID accountId, String status) {
        if (accountId != null && status != null) {
            return jdbc.query("""
                    SELECT * FROM loan.loan_requests WHERE account_id = ? AND status = ?
                    ORDER BY requested_at DESC
                    """, mapper, accountId, status);
        }
        if (accountId != null) {
            return jdbc.query("""
                    SELECT * FROM loan.loan_requests WHERE account_id = ?
                    ORDER BY requested_at DESC
                    """, mapper, accountId);
        }
        if (status != null) {
            return jdbc.query("""
                    SELECT * FROM loan.loan_requests WHERE status = ?
                    ORDER BY requested_at DESC
                    """, mapper, status);
        }
        return jdbc.query("SELECT * FROM loan.loan_requests ORDER BY requested_at DESC", mapper);
    }

    public void updateStatus(UUID loanId, String status, String note) {
        jdbc.update("UPDATE loan.loan_requests SET status = ?, note = COALESCE(?, note) WHERE loan_id = ?",
                status, note, loanId);
    }

    public void markDisbursed(UUID loanId, OffsetDateTime disbursedAt, OffsetDateTime dueAt) {
        jdbc.update("""
                UPDATE loan.loan_requests
                   SET status = 'DISBURSED', disbursed_at = ?, due_at = ?, note = NULL
                 WHERE loan_id = ?
                """, disbursedAt, dueAt, loanId);
    }

    public void markRepaid(UUID loanId, OffsetDateTime repaidAt) {
        jdbc.update("""
                UPDATE loan.loan_requests SET status = 'REPAID', repaid_at = ? WHERE loan_id = ?
                """, repaidAt, loanId);
    }

    public BigDecimal poolBalance() {
        List<BigDecimal> rows = jdbc.query(
                "SELECT available_balance FROM loan.admin_pool LIMIT 1",
                (rs, n) -> rs.getBigDecimal(1));
        return rows.stream().findFirst().orElse(BigDecimal.ZERO);
    }

    public void ensurePool(UUID poolId, BigDecimal initial) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM loan.admin_pool", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        jdbc.update("""
                INSERT INTO loan.admin_pool(pool_id, available_balance, version, updated_at)
                VALUES (?, ?, 0, now())
                ON CONFLICT (pool_id) DO NOTHING
                """, poolId, initial);
    }

    public void updatePool(BigDecimal balance) {
        jdbc.update("""
                UPDATE loan.admin_pool
                   SET available_balance = ?, version = version + 1, updated_at = now()
                 WHERE pool_id = (SELECT pool_id FROM loan.admin_pool LIMIT 1)
                """, balance);
    }

    public void recordReplenishment(UUID eventId, BigDecimal amount, BigDecimal balanceAfter,
                                    String reason, OffsetDateTime at) {
        jdbc.update("""
                INSERT INTO loan.pool_replenishments(event_id, amount, balance_after, reason, occurred_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, eventId, amount, balanceAfter, reason, at);
    }

    public List<LoanViewsRow> replenishments() {
        return jdbc.query("""
                SELECT event_id, amount, balance_after, occurred_at
                FROM loan.pool_replenishments ORDER BY occurred_at DESC
                """, (rs, n) -> new LoanViewsRow(
                rs.getObject("event_id", UUID.class),
                rs.getBigDecimal("amount"),
                rs.getBigDecimal("balance_after"),
                rs.getObject("occurred_at", OffsetDateTime.class)));
    }

    public record LoanViewsRow(UUID eventId, BigDecimal amount, BigDecimal balanceAfter,
                               OffsetDateTime at) {
    }

    // ------------------------------------------------------------------ trust tier cache

    public void upsertTier(UUID accountId, int tier, BigDecimal score) {
        jdbc.update("""
                INSERT INTO loan.trust_tiers(account_id, tier, score, updated_at)
                VALUES (?, ?, ?, now())
                ON CONFLICT (account_id) DO UPDATE
                  SET tier = EXCLUDED.tier, score = EXCLUDED.score, updated_at = now()
                """, accountId, tier, score);
    }

    public int trustTier(UUID accountId) {
        List<Integer> rows = jdbc.query(
                "SELECT tier FROM loan.trust_tiers WHERE account_id = ?",
                (rs, n) -> rs.getInt(1), accountId);
        return rows.stream().findFirst().orElse(1); // Medium until the first trust event arrives
    }
}
