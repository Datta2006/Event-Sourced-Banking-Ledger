package com.bank.trust.controller;

import com.bank.trust.model.TrustViews.TrustScoreChangeView;
import com.bank.trust.model.TrustViews.TrustScoreView;
import com.bank.trust.service.TrustScoreService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Trust-score read API (docs/api_spec/trust-score-service.yaml). */
@RestController
@RequestMapping("/api/trust-score")
public class TrustScoreController {

    private final TrustScoreService trustScores;

    public TrustScoreController(TrustScoreService trustScores) {
        this.trustScores = trustScores;
    }

    @GetMapping("/{accountId}")
    public TrustScoreView get(@PathVariable UUID accountId) {
        return trustScores.get(accountId);
    }

    @GetMapping("/changes")
    public List<TrustScoreChangeView> changes(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(defaultValue = "50") int limit) {
        return trustScores.changes(accountId, limit);
    }
}
