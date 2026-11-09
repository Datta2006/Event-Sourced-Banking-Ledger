package com.bank.trust.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Response records matching docs/api_spec/trust-score-service.yaml. */
public final class TrustViews {

    private TrustViews() {
    }

    public record TrustScoreView(
            UUID accountId,
            int score,
            int tier,
            OffsetDateTime lastChangedAt) {
    }

    public record TrustScoreChangeView(
            UUID eventId,
            UUID accountId,
            int scoreBefore,
            int scoreAfter,
            int tierBefore,
            int tierAfter,
            String reason,
            OffsetDateTime changedAt) {
    }
}
