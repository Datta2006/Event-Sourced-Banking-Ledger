package com.bank.notification.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Response record matching docs/api_spec/notification-service.yaml. */
public record NotificationView(
        UUID notificationId,
        UUID accountId,
        String channel,
        String subject,
        String causeEvent,
        String deliveryStatus,
        OffsetDateTime sentAt) {
}
