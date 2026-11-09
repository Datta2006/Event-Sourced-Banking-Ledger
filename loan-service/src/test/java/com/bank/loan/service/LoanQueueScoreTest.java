package com.bank.loan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Queue score and tier-cache mapping (v2 §3.3, §3.6). */
class LoanQueueScoreTest {

    private static final OffsetDateTime T0 =
            OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void higherTrustTierAlwaysOutranksArrivalTime() {
        double tier1MuchEarlier = LoanService.score(1, T0);
        double tier2MuchLater = LoanService.score(2, T0.plusDays(365));

        assertTrue(tier2MuchLater < tier1MuchEarlier,
                "tier 2 must outrank tier 1 even a year later");
    }

    @Test
    void sameTierIsFirstComeFirstServed() {
        double first = LoanService.score(1, T0);
        double second = LoanService.score(1, T0.plusSeconds(4));

        assertTrue(first < second, "earlier request must pop first inside a tier");
    }

    @Test
    void scoreEncodesTierAsLeadingDigit() {
        assertEquals(2e13, LoanService.score(1, OffsetDateTime.ofInstant(
                java.time.Instant.EPOCH, ZoneOffset.UTC)), 0.1);
        assertEquals(1e13, LoanService.score(2, OffsetDateTime.ofInstant(
                java.time.Instant.EPOCH, ZoneOffset.UTC)), 0.1);
        assertEquals(0.0, LoanService.score(3, OffsetDateTime.ofInstant(
                java.time.Instant.EPOCH, ZoneOffset.UTC)), 0.1);
    }

    @Test
    void trustTierEventNamesMapToNumericTiers() {
        TrustTierService service = new TrustTierService(null, new ObjectMapper());
        assertEquals(0, service.tierOf("LOW", 10));
        assertEquals(1, service.tierOf("MEDIUM", 50));
        assertEquals(2, service.tierOf("HIGH", 80));
        assertEquals(3, service.tierOf("EXCELLENT", 95));
        assertEquals(1, service.tierOf(null, 70));
        assertEquals(2, service.tierOf(null, 71));
        assertEquals(3, service.tierOf(null, 91));
    }
}
