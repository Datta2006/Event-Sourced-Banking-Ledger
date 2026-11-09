package com.bank.loan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/** Maps {@code TrustScoreChanged} events into the local 0–3 tier cache. */
@Service
public class TrustTierService {

    private static final Logger log = LoggerFactory.getLogger(TrustTierService.class);

    private final LoanRepository loanRepository;
    private final ObjectMapper objectMapper;

    public TrustTierService(LoanRepository loanRepository, ObjectMapper objectMapper) {
        this.loanRepository = loanRepository;
        this.objectMapper = objectMapper;
    }

    public void apply(String envelopeJson) {
        if (envelopeJson == null || envelopeJson.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(envelopeJson);
            JsonNode payload = root.path("payload");
            String accountId = text(payload, "accountId");
            if (accountId == null) {
                accountId = text(root, "aggregateId");
            }
            if (accountId == null) {
                return;
            }
            String tierName = text(payload, "newTier");
            int tier = tierOf(tierName, payload.path("newScore").asDouble(50));
            BigDecimal score = payload.hasNonNull("newScore")
                    ? BigDecimal.valueOf(payload.path("newScore").asDouble()) : null;
            loanRepository.upsertTier(UUID.fromString(accountId), tier, score);
        } catch (Exception e) {
            log.warn("Could not apply trust event: {}", e.getMessage());
        }
    }

    public int tierOf(String tierName, double score) {
        if (tierName != null) {
            return switch (tierName.trim().toUpperCase()) {
                case "LOW" -> 0;
                case "MEDIUM" -> 1;
                case "HIGH" -> 2;
                case "EXCELLENT" -> 3;
                default -> scoreTier(score);
            };
        }
        return scoreTier(score);
    }

    private static int scoreTier(double score) {
        if (score <= 40) {
            return 0;
        }
        if (score <= 70) {
            return 1;
        }
        if (score <= 90) {
            return 2;
        }
        return 3;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
