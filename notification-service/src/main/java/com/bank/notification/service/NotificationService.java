package com.bank.notification.service;

import com.bank.notification.model.NotificationView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns domain events into customer emails (MailHog in dev) and keeps the
 * in-memory outbox behind {@code GET /api/notifications}.
 *
 * <p>No database by design (DATA_MODEL §6): the outbox is a demo projection;
 * MailHog holds the delivered mail. Kafka redelivery is deduped by eventId.</p>
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;
    private final String fromAddress;
    private final String emailDomain;
    private final int maxOutbox;

    private final List<NotificationView> outbox = new ArrayList<>();
    private final Map<String, Boolean> processedEvents = new LinkedHashMap<>();

    public NotificationService(JavaMailSender mailSender,
                               ObjectMapper objectMapper,
                               @Value("${app.notification.from:no-reply@ddbs-bank.example}") String fromAddress,
                               @Value("${app.notification.email-domain:example.com}") String emailDomain,
                               @Value("${app.notification.max-outbox:500}") int maxOutbox) {
        this.mailSender = mailSender;
        this.objectMapper = objectMapper;
        this.fromAddress = fromAddress;
        this.emailDomain = emailDomain;
        this.maxOutbox = maxOutbox;
    }

    /** Apply one Kafka envelope from any business topic. */
    public void consume(String topic, String envelopeJson) {
        if (envelopeJson == null || envelopeJson.isBlank()) {
            return;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(envelopeJson);
        } catch (Exception e) {
            log.warn("Ignoring malformed {} event: {}", topic, e.getMessage());
            return;
        }
        String type = text(root, "eventType");
        if (type == null) {
            type = text(root, "type");
        }
        if (type == null) {
            return;
        }
        String eventId = text(root, "eventId");
        if (eventId == null) {
            eventId = UUID.nameUUIDFromBytes(envelopeJson.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .toString();
        }
        synchronized (processedEvents) {
            if (processedEvents.containsKey(eventId)) {
                return;
            }
            processedEvents.put(eventId, Boolean.TRUE);
            if (processedEvents.size() > 20_000) {
                var iterator = processedEvents.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
        }
        JsonNode payload = root.path("payload");
        String accountText = text(payload, "accountId");
        if (accountText == null) {
            accountText = text(root, "aggregateId");
        }
        if (accountText == null) {
            return;
        }
        UUID accountId;
        try {
            accountId = UUID.fromString(accountText);
        } catch (IllegalArgumentException e) {
            return;
        }
        String message = messageFor(type, payload);
        if (message == null) {
            return;
        }
        send(accountId, subjectFor(type), message, type);
    }

    // ------------------------------------------------------------------ outbox

    public List<NotificationView> list(UUID accountId, String causeEvent) {
        synchronized (outbox) {
            return outbox.stream()
                    .filter(n -> accountId == null || accountId.equals(n.accountId()))
                    .filter(n -> causeEvent == null || causeEvent.isBlank()
                            || causeEvent.equals(n.causeEvent()))
                    .toList();
        }
    }

    // ------------------------------------------------------------------ delivery

    private void send(UUID accountId, String subject, String body, String causeEvent) {
        String email = accountId + "@" + emailDomain;
        String status;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(email);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            status = "SENT";
        } catch (Exception e) {
            // MailHog unavailable: keep the record visible, marked for retry.
            status = "RETRYING";
            log.warn("Could not send notification to {}: {}", email, e.getMessage());
        }
        NotificationView view = new NotificationView(UUID.randomUUID(), accountId, "EMAIL",
                subject, causeEvent, status, OffsetDateTime.now());
        synchronized (outbox) {
            outbox.add(0, view);
            while (outbox.size() > maxOutbox) {
                outbox.remove(outbox.size() - 1);
            }
        }
        log.info("Notification {} ({}) for {}: {}", status, causeEvent, accountId, subject);
    }

    private static String subjectFor(String type) {
        return switch (type) {
            case "AccountOpened" -> "Welcome to DDBS Bank";
            case "AccountClosed" -> "Your account is closed";
            case "MoneyDeposited" -> "Deposit received";
            case "MoneyWithdrawn" -> "Withdrawal processed";
            case "TransferCompleted" -> "Transfer completed";
            case "TransferFailed" -> "Transfer failed";
            case "LoanApproved" -> "Loan approved";
            case "LoanRejected" -> "Loan request declined";
            case "LoanDisbursed" -> "Loan disbursed";
            case "LoanRepaidOnTime", "LoanRepaidEarly", "LoanRepaidLate" -> "Loan repayment received";
            case "LoanDefaulted" -> "Loan default recorded";
            case "FundsReserved" -> "Offline payment hold placed";
            case "FundsCaptured" -> "Payment captured";
            case "FundsReleased" -> "Payment hold released";
            case "TrustScoreChanged" -> "Trust score updated";
            case "FraudCaseOpened" -> "Suspicious activity flagged";
            case "FraudCaseReviewed" -> "Fraud review completed";
            default -> "Account update";
        };
    }

    private static String messageFor(String type, JsonNode payload) {
        String amount = text(payload, "amount");
        String tail = amount == null ? "" : " Amount: " + amount + ".";
        return switch (type) {
            case "AccountOpened" -> "Your DDBS Bank account was opened successfully.";
            case "AccountClosed" -> "Your DDBS Bank account has been closed.";
            case "MoneyDeposited" -> "We received a deposit into your account." + tail;
            case "MoneyWithdrawn" -> "A withdrawal was made from your account." + tail;
            case "TransferCompleted" -> "Your transfer completed successfully." + tail;
            case "TransferFailed" -> "Your transfer could not be completed and was reversed." + tail;
            case "LoanApproved" -> "Your loan request was approved." + tail;
            case "LoanRejected" -> "Your loan request was declined.";
            case "LoanDisbursed" -> "Your loan was disbursed to your account." + tail;
            case "LoanRepaidOnTime", "LoanRepaidEarly", "LoanRepaidLate" ->
                    "Your loan repayment was received." + tail;
            case "LoanDefaulted" -> "A loan default was recorded on your account.";
            case "FundsReserved" -> "A hold was placed for your offline payment." + tail;
            case "FundsCaptured" -> "Your offline payment was captured." + tail;
            case "FundsReleased" -> "Your payment hold was released." + tail;
            case "TrustScoreChanged" -> {
                String score = text(payload, "newScore");
                yield "Your trust score is now " + (score == null ? "updated" : score) + ".";
            }
            case "FraudCaseOpened" -> "We flagged unusual activity on your account for review.";
            case "FraudCaseReviewed" -> "DISMISSED".equalsIgnoreCase(text(payload, "status"))
                    ? "The fraud flag on your account was cleared."
                    : "Your fraud case was reviewed by our team.";
            default -> null;
        };
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
