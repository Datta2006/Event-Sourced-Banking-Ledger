package com.bank.fraud.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bank.fraud.service.FraudDetectionService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for fraud detection service.
 * Serves the fraud-case list the UI consumes; rules stay internal.
 */
@RestController
@RequestMapping("/fraud")
public class FraudDetectionController {

    @Autowired
    private FraudDetectionService fraudDetectionService;

    /**
     * Fraud cases, optionally filtered. The UI's FraudAlerts/FraudReview
     * screens poll this (GET /api/fraud/cases through the gateway).
     */
    @GetMapping("/cases")
    public List<Map<String, Object>> listCases(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) String status) {
        return fraudDetectionService.listCases(accountId, status);
    }

    /**
     * Health check endpoint (kept under the legacy path too).
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Fraud Detection Service is running");
    }
}