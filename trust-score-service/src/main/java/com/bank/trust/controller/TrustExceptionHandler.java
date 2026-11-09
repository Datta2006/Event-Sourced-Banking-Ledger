package com.bank.trust.controller;

import com.bank.trust.service.TrustScoreService.TrustExceptions;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Maps trust-score failures to the API spec's status codes. */
@RestControllerAdvice
public class TrustExceptionHandler {

    @ExceptionHandler(TrustExceptions.NotFound.class)
    public ResponseEntity<Map<String, Object>> notFound(TrustExceptions.NotFound e) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", 404);
        payload.put("error", "Not Found");
        payload.put("message", e.getMessage());
        payload.put("timestamp", OffsetDateTime.now().toString());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(payload);
    }
}
