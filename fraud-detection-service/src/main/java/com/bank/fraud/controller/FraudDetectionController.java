package com.bank.fraud.controller;

import com.bank.fraud.model.FraudCase;
import com.bank.fraud.service.FraudDetectionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Fraud case read/review API (docs/api_spec/fraud-detection-service.yaml). */
@RestController
@RequestMapping("/api/fraud/cases")
public class FraudDetectionController {

    public record ReviewBody(String status, String note) {
    }

    private final FraudDetectionService fraudDetection;

    public FraudDetectionController(FraudDetectionService fraudDetection) {
        this.fraudDetection = fraudDetection;
    }

    @GetMapping
    public List<FraudCase> listCases(@RequestParam(required = false) UUID accountId,
                                     @RequestParam(required = false) String status) {
        return fraudDetection.listCases(accountId, status);
    }

    @GetMapping("/{caseId}")
    public FraudCase getCase(@PathVariable UUID caseId) {
        return fraudDetection.getCase(caseId);
    }

    @PostMapping("/{caseId}/review")
    public FraudCase review(@PathVariable UUID caseId,
                            @RequestBody ReviewBody body,
                            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        String status = body == null ? null : body.status();
        String note = body == null ? null : body.note();
        return fraudDetection.review(caseId, status, note, key);
    }
}
