package com.bank.payment.client;

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

/** Account-service internal API: reserve/capture/release and direct debit/credit. */
@Component
public class AccountClient {

    private static final Logger log = LoggerFactory.getLogger(AccountClient.class);

    private final RestClient client;

    public AccountClient(@Value("${app.account-service.url:http://localhost:8081}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    public void reserve(UUID accountId, UUID reservationId, BigDecimal amount, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reservationId", reservationId);
        body.put("amount", amount);
        post("/internal/accounts/{accountId}/reservations", accountId,
                "payment:reserve:" + reservationId, body, correlationId);
    }

    public void capture(UUID accountId, UUID reservationId, BigDecimal amount, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        post("/internal/accounts/{accountId}/reservations/{reservationId}/capture", accountId,
                "payment:capture:" + reservationId, body, correlationId, reservationId);
    }

    public void release(UUID accountId, UUID reservationId, BigDecimal amount, String reason,
                        String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        body.put("reason", reason);
        post("/internal/accounts/{accountId}/reservations/{reservationId}/release", accountId,
                "payment:release:" + reservationId + ":" + reason, body, correlationId, reservationId);
    }

    public void debit(UUID accountId, BigDecimal amount, String reference, String correlationId) {
        transfer(accountId, amount, reference, correlationId, "debit");
    }

    public void credit(UUID accountId, BigDecimal amount, String reference, String correlationId) {
        transfer(accountId, amount, reference, correlationId, "credit");
    }

    private void transfer(UUID accountId, BigDecimal amount, String reference,
                          String correlationId, String action) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        body.put("reference", reference);
        body.put("deviceId", "payment-service");
        post("/internal/accounts/{accountId}/" + action, accountId,
                "payment:" + reference, body, correlationId);
    }

    private void post(String uri, UUID accountId, String idempotencyKey,
                      Map<String, Object> body, String correlationId, Object... uriVars) {
        Object[] vars = new Object[uriVars.length + 1];
        vars[0] = accountId;
        System.arraycopy(uriVars, 0, vars, 1, uriVars.length);
        try {
            client.post().uri(uri, vars)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", idempotencyKey)
                    .header("X-Correlation-Id", correlationId == null ? idempotencyKey : correlationId)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.warn("Account call {} failed: {} {}", uri, e.getStatusCode(), e.getResponseBodyAsString());
            throw new AccountCallException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RuntimeException e) {
            log.warn("Account call {} unreachable: {}", uri, e.getMessage());
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
