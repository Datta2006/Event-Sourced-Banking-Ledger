package com.bank.trust.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Basic CRUD/finder access to trust_scores. */
public interface TrustScoreRepository extends JpaRepository<TrustScore, UUID> {

    /** Immutable-entity score update: bump score + tier + last_changed_at. */
    @Modifying
    @Query("update TrustScore t set t.score = :score, t.tier = :tier, t.lastChangedAt = :changedAt " +
           "where t.accountId = :accountId")
    int updateScore(@Param("accountId") UUID accountId,
                    @Param("score") int score,
                    @Param("tier") short tier,
                    @Param("changedAt") OffsetDateTime changedAt);
}
