package com.bank.fraud.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Response record matching docs/api_spec/fraud-detection-service.yaml. */
public record FraudCase(
        UUID caseId,
        UUID accountId,
        String rule,
        int riskScore,
        String triggeredByEvent,
        String status,
        OffsetDateTime flaggedAt,
        OffsetDateTime reviewedAt) {

    public FraudCase reviewed(String newStatus, OffsetDateTime at) {
        return new FraudCase(caseId, accountId, rule, riskScore, triggeredByEvent,
                newStatus, flaggedAt, at);
    }
}
