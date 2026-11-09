package com.bank.trust.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Boundary tests for the documented trust-score rules (v2 §3.6). */
class TrustScoreServiceTest {

    @Test
    void tierBoundariesMatchTheSpec() {
        assertEquals(0, TrustScoreService.tierOf(0));
        assertEquals(0, TrustScoreService.tierOf(40));
        assertEquals(1, TrustScoreService.tierOf(41));
        assertEquals(1, TrustScoreService.tierOf(70));
        assertEquals(2, TrustScoreService.tierOf(71));
        assertEquals(2, TrustScoreService.tierOf(90));
        assertEquals(3, TrustScoreService.tierOf(91));
        assertEquals(3, TrustScoreService.tierOf(100));
    }

    @Test
    void halfPointPerTenThousandAccumulatesAsOnePointPerTwentyThousand() {
        assertEquals(0, TrustScoreService.depositPoints(new BigDecimal("19999.99")));
        assertEquals(1, TrustScoreService.depositPoints(new BigDecimal("20000")));
        assertEquals(1, TrustScoreService.depositPoints(new BigDecimal("39999")));
        assertEquals(2, TrustScoreService.depositPoints(new BigDecimal("44000")));
        assertEquals(5, TrustScoreService.depositPoints(new BigDecimal("100000")));
    }

    @Test
    void dailyDepositPointsAreCappedAtFive() {
        assertEquals(5, TrustScoreService.depositPoints(new BigDecimal("200000")));
        assertEquals(5, TrustScoreService.depositPoints(new BigDecimal("999999")));
        assertEquals(0, TrustScoreService.depositPoints(BigDecimal.ZERO));
        assertEquals(0, TrustScoreService.depositPoints(null));
    }
}
