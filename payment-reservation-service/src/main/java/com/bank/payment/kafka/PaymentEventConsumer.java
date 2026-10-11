package com.bank.payment.kafka;

import com.bank.payment.persistence.AccountBalance;
import com.bank.payment.persistence.AccountBalanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kafka consumer feeding the payment schema's balance projection.
 * Account events (opened/deposited/withdrawn) maintain available balance so
 * reservation checks never join across schemas at runtime (ADR-002).
 */
@Component
public class PaymentEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(PaymentEventConsumer.class);
    private static final Pattern AMOUNT = Pattern.compile("\"amount\"\\s*:\\s*([0-9.]+)");

    @Autowired
    private AccountBalanceRepository accountBalanceRepository;

    /**
     * Upsert the balance projection from raw event payloads (string-jelly
     * parsing kept deliberately dependency-free; the event catalog owns shape).
     */
    @KafkaListener(topics = "account.events", groupId = "payment-reservation-service")
    public void processAccountEvent(String event) {
        if (event == null || event.isBlank()) return;
        if (event.contains("AccountOpened")) {
            String id = extract(event, "\"accountId\"\\s*:\\s*\"([^"]+)\"");
            if (id != null) {
                BigDecimal initial = amount(event, BigDecimal.ZERO);
                AccountBalance b = accountBalanceRepository.findById(id).orElseGet(() -> new AccountBalance(id, "ACTIVE", BigDecimal.ZERO, BigDecimal.ZERO));
                b.setAvailableBalance(b.getAvailableBalance().add(initial));
                accountBalanceRepository.save(b);
            }
        } else if (event.contains("MoneyDeposited")) {
            String id = extract(event, "\"aggregateId\"\\s*:\\s*\"([^"]+)\"");
            BigDecimal amt = amount(event, null);
            if (id != null && amt != null) {
                AccountBalance b = accountBalanceRepository.findById(id).orElseGet(() -> new AccountBalance(id, "ACTIVE", BigDecimal.ZERO, BigDecimal.ZERO));
                b.setAvailableBalance(b.getAvailableBalance().add(amt));
                accountBalanceRepository.save(b);
            }
        } else if (event.contains("MoneyWithdrawn")) {
            String id = extract(event, "\"aggregateId\"\\s*:\\s*\"([^"]+)\"");
            BigDecimal amt = amount(event, null);
            if (id != null && amt != null) {
                AccountBalance b = accountBalanceRepository.findById(id).orElse(null);
                if (b != null) {
                    b.setAvailableBalance(b.getAvailableBalance().subtract(amt));
                    accountBalanceRepository.save(b);
                }
            }
        }
    }

    /**
     * Process payment events for monitoring/auditing.
     */
    @KafkaListener(topics = "payment.events", groupId = "payment-reservation-service")
    public void processPaymentEvent(String event) {
        logger.debug("Received payment event: {}", event);
        // For monitoring/auditing purposes
    }

    private String extract(String s, String regex) {
        Matcher m = Pattern.compile(regex).matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private BigDecimal amount(String s, BigDecimal dflt) {
        Matcher m = AMOUNT.matcher(s);
        return m.find() ? new BigDecimal(m.group(1)) : dflt;
    }
}