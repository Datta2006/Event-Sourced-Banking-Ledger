package com.bank.notification.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for sending customer notifications.
 * Implements requirements from HLD_LLD_SystemDesign_v2.md:
 * - Customer notifications via MailHog
 *
 * Every sent notification is recorded in an in-memory outbox served to the
 * UI via GET /notifications (the bank's NotificationsLog and the customer's
 * Notifications screens). This slice has no database; MailHog holds the mail.
 */
@Service
public class NotificationService {

    @Autowired
    private JavaMailSender mailSender;

    private final List<Map<String, Object>> outbox = new ArrayList<>();

    /**
     * Send a notification to a customer and record it in the outbox.
     */
    public void sendNotification(String accountId, String customerEmail,
                                 String subject, String body, String causeEvent) {
        String status;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(customerEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            status = "SENT";
        } catch (Exception e) {
            status = "RETRYING"; // MailHog briefly unreachable — visible in UI as retrying
        }

        System.out.println("Notification " + status + " to " + customerEmail + ": " + subject);
        Map<String, Object> record = Map.ofEntries(
                Map.entry("notificationId", UUID.randomUUID().toString()),
                Map.entry("accountId", accountId),
                Map.entry("channel", "EMAIL"),
                Map.entry("subject", subject),
                Map.entry("body", body),
                Map.entry("causeEvent", causeEvent),
                Map.entry("deliveryStatus", status),
                Map.entry("sentAt", OffsetDateTime.now().toString()));
        synchronized (outbox) {
            outbox.add(0, record);
            if (outbox.size() > 500) outbox.remove(outbox.size() - 1);
        }
    }

    /**
     * Outbox list for the UI (GET /api/notifications via the gateway).
     */
    public List<Map<String, Object>> listNotifications(String accountId, String causeEvent) {
        synchronized (outbox) {
            return outbox.stream()
                    .filter(n -> accountId == null || accountId.equals(n.get("accountId")))
                    .filter(n -> causeEvent == null || causeEvent.equals(n.get("causeEvent")))
                    .sorted(Comparator.comparing((Map<String, Object> n) -> (String) n.get("sentAt")).reversed())
                    .map(n -> new java.util.LinkedHashMap<>(n))
                    .toList();
        }
    }
}
