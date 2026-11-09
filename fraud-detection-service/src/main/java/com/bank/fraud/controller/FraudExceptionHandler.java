package com.bank.fraud.controller;

import com.bank.fraud.service.FraudDetectionService.FraudExceptions;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Maps fraud failures to the API spec's status codes. */
@RestControllerAdvice
public class FraudExceptionHandler {

    @ExceptionHandler(FraudExceptions.NotFound.class)
    public ResponseEntity<Map<String, Object>> notFound(FraudExceptions.NotFound e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(FraudExceptions.Conflict.class)
    public ResponseEntity<Map<String, Object>> conflict(FraudExceptions.Conflict e) {
        return body(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(FraudExceptions.BadRequest.class)
    public ResponseEntity<Map<String, Object>> badRequest(FraudExceptions.BadRequest e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
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
