package com.bank.trust.controller;

import com.bank.trust.persistence.TrustScore;
import com.bank.trust.service.TrustScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for trust score operations.
 * Handles HTTP requests and delegates to the TrustScoreService.
 */
@RestController
@RequestMapping("/trust-score")
public class TrustScoreController {

    @Autowired
    private TrustScoreService trustScoreService;

    /**
     * Get trust score for an account.
     */
    @GetMapping("/{accountId}")
    public ResponseEntity<TrustScore> getTrustScore(@PathVariable UUID accountId) {
        TrustScore trustScore = trustScoreService.getOrCreateTrustScore(accountId);
        return ResponseEntity.ok(trustScore);
    }
}