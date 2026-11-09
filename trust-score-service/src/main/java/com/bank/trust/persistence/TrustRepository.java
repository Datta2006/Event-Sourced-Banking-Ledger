package com.bank.trust.persistence;

import com.bank.trust.model.TrustViews.TrustScoreChangeView;
import com.bank.trust.model.TrustViews.TrustScoreView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC access to the Flyway-managed {@code trust_score} schema. */
@Repository
public class TrustRepository {

    public record ScoreRow(UUID accountId, int score, int tier, OffsetDateTime lastChangedAt) {
    }

    public record DepositRow(BigDecimal totalDeposited, int pointsAwarded) {
    }

    private final JdbcTemplate jdbc;

    private final RowMapper<ScoreRow> scoreMapper = (rs, n) -> new ScoreRow(
            rs.getObject("account_id", UUID.class),
            rs.getInt("score"),
            rs.getInt("tier"),
            rs.getObject("last_changed_at", OffsetDateTime.class));

    private final RowMapper<TrustScoreChangeView> changeMapper = (rs, n) ->
            new TrustScoreChangeView(
                    rs.getObject("event_id", UUID.class),
                    rs.getObject("account_id", UUID.class),
                    rs.getInt("score_before"),
                    rs.getInt("score_after"),
                    rs.getInt("tier_before"),
                    rs.getInt("tier_after"),
                    rs.getString("reason"),
                    rs.getObject("changed_at", OffsetDateTime.class));

    public TrustRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** At-least-once Kafka delivery, exactly-once effect. */
    public boolean claimEvent(UUID eventId, String topic) {
        return jdbc.update("""
                INSERT INTO trust_score.processed_events (event_id, topic)
                VALUES (?, ?) ON CONFLICT (event_id) DO NOTHING
                """, eventId, topic) == 1;
    }

    /** New accounts start at 50 (Medium), per v2 §3.6. */
    public void ensureAccount(UUID accountId, OffsetDateTime now) {
        jdbc.update("""
                INSERT INTO trust_score.trust_scores (account_id, score, tier, last_changed_at)
                VALUES (?, 50, 1, ?) ON CONFLICT (account_id) DO NOTHING
                """, accountId, now);
    }

    public Optional<ScoreRow> findScore(UUID accountId) {
        return jdbc.query("SELECT * FROM trust_score.trust_scores WHERE account_id = ?",
                scoreMapper, accountId).stream().findFirst();
    }

    public Optional<ScoreRow> lockScore(UUID accountId) {
        return jdbc.query("SELECT * FROM trust_score.trust_scores WHERE account_id = ? FOR UPDATE",
                scoreMapper, accountId).stream().findFirst();
    }

    public void updateScore(UUID accountId, int score, int tier, OffsetDateTime changedAt) {
        jdbc.update("""
                UPDATE trust_score.trust_scores
                   SET score = ?, tier = ?, last_changed_at = ?
                 WHERE account_id = ?
                """, score, tier, changedAt, accountId);
    }

    public DepositRow lockDailyDeposits(UUID accountId, LocalDate day) {
        jdbc.update("""
                INSERT INTO trust_score.deposit_daily (account_id, day)
                VALUES (?, ?) ON CONFLICT (account_id, day) DO NOTHING
                """, accountId, day);
        return jdbc.query("""
                SELECT total_deposited, points_awarded
                  FROM trust_score.deposit_daily
                 WHERE account_id = ? AND day = ?
                 FOR UPDATE
                """, (rs, n) -> new DepositRow(rs.getBigDecimal("total_deposited"),
                        rs.getInt("points_awarded")), accountId, day).get(0);
    }

    public void updateDailyDeposits(UUID accountId, LocalDate day,
                                    BigDecimal total, int pointsAwarded) {
        jdbc.update("""
                UPDATE trust_score.deposit_daily
                   SET total_deposited = ?, points_awarded = ?
                 WHERE account_id = ? AND day = ?
                """, total, pointsAwarded, accountId, day);
    }

    public void insertChange(TrustScoreChangeView change) {
        jdbc.update("""
                INSERT INTO trust_score.trust_score_changes
                  (event_id, account_id, score_before, score_after,
                   tier_before, tier_after, reason, changed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, change.eventId(), change.accountId(), change.scoreBefore(),
                change.scoreAfter(), change.tierBefore(), change.tierAfter(),
                change.reason(), change.changedAt());
    }

    public List<TrustScoreChangeView> listChanges(UUID accountId, int limit) {
        int capped = Math.max(1, Math.min(limit, 200));
        if (accountId == null) {
            return jdbc.query("""
                    SELECT * FROM trust_score.trust_score_changes
                     ORDER BY changed_at DESC LIMIT ?
                    """, changeMapper, capped);
        }
        return jdbc.query("""
                SELECT * FROM trust_score.trust_score_changes
                 WHERE account_id = ? ORDER BY changed_at DESC LIMIT ?
                """, changeMapper, accountId, capped);
    }

    public TrustScoreView view(ScoreRow row) {
        return new TrustScoreView(row.accountId(), row.score(), row.tier(),
                row.lastChangedAt());
    }
}
