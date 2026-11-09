package com.bank.payment.controller;

import com.bank.payment.model.PaymentViews.PaymentRequest;
import com.bank.payment.model.PaymentViews.PaymentResult;
import com.bank.payment.service.PaymentReservationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Online payment endpoint (synchronous debit/credit through Account Command). */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentReservationService payments;

    public PaymentController(PaymentReservationService payments) {
        this.payments = payments;
    }

    @PostMapping
    public PaymentResult pay(@RequestBody PaymentRequest request,
                             @RequestHeader(value = "Idempotency-Key", required = false) String key,
                             @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        String idempotencyKey = key == null || key.isBlank() ? UUID.randomUUID().toString() : key;
        String correlation = correlationId == null || correlationId.isBlank()
                ? idempotencyKey : correlationId;
        return payments.payOnline(request, idempotencyKey, correlation);
    }
}
