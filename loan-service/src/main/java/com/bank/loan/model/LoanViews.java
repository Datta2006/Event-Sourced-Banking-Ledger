package com.bank.loan.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Response DTOs matching docs/api_spec/loan-service.yaml. */
public final class LoanViews {

    private LoanViews() {
    }

    public record LoanRequestView(
            UUID loanId,
            UUID accountId,
            BigDecimal amount,
            String purpose,
            int trustTier,
            String status,
            OffsetDateTime requestedAt,
            OffsetDateTime disbursedAt,
            OffsetDateTime dueAt,
            OffsetDateTime repaidAt,
            String note) {
    }

    public record LoanQueueEntry(
            UUID loanId,
            UUID accountId,
            BigDecimal amount,
            int trustTier,
            String status,
            int position,
            OffsetDateTime requestedAt) {
    }

    public record LoanQueueView(List<LoanQueueEntry> entries, OffsetDateTime generatedAt) {
    }

    public record AdminPoolView(BigDecimal balance, String currency, OffsetDateTime updatedAt) {
    }

    public record PoolReplenishmentView(UUID eventId, BigDecimal amount, BigDecimal balanceAfter,
                                        OffsetDateTime at) {
    }

    public record DecisionRequest(String decision, String note) {
    }

    public record RepaymentRequest(BigDecimal amount) {
    }

    public record AmountRequest(BigDecimal amount) {
    }
}
