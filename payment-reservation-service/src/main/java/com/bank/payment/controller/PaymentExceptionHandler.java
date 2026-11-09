package com.bank.payment.controller;

import com.bank.payment.service.PaymentReservationService.PaymentExceptions;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Maps domain failures to the status codes in the payment API spec. */
@RestControllerAdvice
public class PaymentExceptionHandler {

    @ExceptionHandler(PaymentExceptions.NotFound.class)
    public ResponseEntity<Map<String, Object>> notFound(PaymentExceptions.NotFound e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(PaymentExceptions.Conflict.class)
    public ResponseEntity<Map<String, Object>> conflict(PaymentExceptions.Conflict e) {
        return body(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(PaymentExceptions.BadRequest.class)
    public ResponseEntity<Map<String, Object>> badRequest(PaymentExceptions.BadRequest e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(PaymentExceptions.Gone.class)
    public ResponseEntity<Map<String, Object>> gone(PaymentExceptions.Gone e) {
        return body(HttpStatus.GONE, e.getMessage());
    }

    @ExceptionHandler(PaymentExceptions.Upstream.class)
    public ResponseEntity<Map<String, Object>> upstream(PaymentExceptions.Upstream e) {
        return body(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status.value());
        payload.put("error", status.getReasonPhrase());
        payload.put("message", message);
        payload.put("timestamp", OffsetDateTime.now().toString());
        return ResponseEntity.status(status).body(payload);
    }
}
