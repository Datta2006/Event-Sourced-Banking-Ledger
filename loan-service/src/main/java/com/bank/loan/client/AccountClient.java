package com.bank.loan.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Calls the account service's internal credit/debit endpoints. */
@Component
public class AccountClient {

    private static final Logger log = LoggerFactory.getLogger(AccountClient.class);

    private final RestClient client;

    public AccountClient(@Value("${app.account-service.url:http://localhost:8081}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    public void credit(UUID accountId, BigDecimal amount, String reference, String correlationId) {
        call(accountId, amount, reference, correlationId, "credit");
    }

    public void debit(UUID accountId, BigDecimal amount, String reference, String correlationId) {
        call(accountId, amount, reference, correlationId, "debit");
    }

    private void call(UUID accountId, BigDecimal amount, String reference,
                      String correlationId, String action) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        body.put("reference", reference);
        body.put("deviceId", "loan-service");
        try {
            client.post()
                    .uri("/internal/accounts/{accountId}/" + action, accountId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", "loan:" + reference)
                    .header("X-Correlation-Id", correlationId == null ? reference : correlationId)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.warn("Account {} {} failed: {} {}", action, accountId,
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new AccountCallException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RuntimeException e) {
            log.warn("Account {} {} unreachable: {}", action, accountId, e.getMessage());
            throw new AccountCallException(503, e.getMessage());
        }
    }

    public static class AccountCallException extends RuntimeException {
        private final int status;

        public AccountCallException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
