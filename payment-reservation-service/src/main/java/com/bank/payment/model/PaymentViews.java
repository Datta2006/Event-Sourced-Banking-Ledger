package com.bank.payment.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Response/request DTOs matching docs/api_spec/payment-reservation-service.yaml. */
public final class PaymentViews {

    private PaymentViews() {
    }

    public record ReservationView(
            UUID reservationId,
            UUID accountId,
            BigDecimal amount,
            String status,
            OffsetDateTime expiresAt,
            OffsetDateTime capturedAt,
            String offlineToken) {
    }

    public record ReserveRequest(UUID accountId, BigDecimal amount, Integer ttlMinutes) {
    }

    public record CaptureRequest(String offlineToken, UUID merchantAccountId) {
    }

    public record PaymentRequest(UUID fromAccountId, UUID toAccountId, BigDecimal amount) {
    }

    public record PaymentResult(UUID paymentId, String status) {
    }
}
