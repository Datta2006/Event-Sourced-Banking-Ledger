package com.bank.trust.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Trust score — docs/DATA_MODEL.md §5, v2 §3.6.
 * Score 0–100; tier 0=Low (0–40), 1=Medium (41–70), 2=High (71–90),
 * 3=Excellent (91–100).
 */
@Entity
@Table(name = "trust_scores")
public class TrustScore {

    @Id
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "score")
    private Integer score;

    @Column(name = "tier")
    private Short tier;

    @Column(name = "last_changed_at")
    private OffsetDateTime lastChangedAt;

    protected TrustScore() {
    }

    public TrustScore(UUID accountId, Integer score, Short tier, OffsetDateTime lastChangedAt) {
        this.accountId = accountId;
        this.score = score;
        this.tier = tier;
        this.lastChangedAt = lastChangedAt;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public Integer getScore() {
        return score;
    }

    public Short getTier() {
        return tier;
    }

    public OffsetDateTime getLastChangedAt() {
        return lastChangedAt;
    }
}
