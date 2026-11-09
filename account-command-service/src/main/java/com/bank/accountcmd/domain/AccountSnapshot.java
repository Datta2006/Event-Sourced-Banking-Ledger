package com.bank.accountcmd.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Public account projection returned by the Account Command Service. */
public record AccountSnapshot(
        UUID accountId,
        String holderName,
        String region,
        BigDecimal availableBalance,
        BigDecimal reservedBalance,
        String status,
        String kycStatus,
        OffsetDateTime openedAt,
        int lastVersion) {
}
