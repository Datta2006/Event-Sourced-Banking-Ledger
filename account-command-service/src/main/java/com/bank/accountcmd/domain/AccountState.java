package com.bank.accountcmd.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * In-memory fold of an account aggregate over its event stream. The event store is
 * the source of truth: this class is recomputed on every read/command, so there is no
 * separately-updated balance column that can drift.
 */
public class AccountState {

    private boolean exists;
    private UUID accountId;
    private String holderName;
    private String status = "ACTIVE";
    private String region;
    private String kycStatus = "PENDING";
    private BigDecimal availableBalance = BigDecimal.ZERO;
    private BigDecimal reservedBalance = BigDecimal.ZERO;
    private OffsetDateTime openedAt;
    private int lastVersion = -1;

    public static AccountState empty(UUID accountId) {
        AccountState s = new AccountState();
        s.accountId = accountId;
        return s;
    }

    public void apply(String type, JsonNode payload) {
        BigDecimal amount = payload.path("amount").isNumber()
                ? payload.path("amount").decimalValue()
                : BigDecimal.ZERO;
        switch (type) {
            case "AccountOpened" -> {
                exists = true;
                holderName = text(payload, "holderName");
                region = text(payload, "region");
                kycStatus = payload.hasNonNull("kycStatus") ? text(payload, "kycStatus") : "PENDING";
                status = "ACTIVE";
                openedAt = payload.hasNonNull("openedAt")
                        ? OffsetDateTime.parse(payload.get("openedAt").asText())
                        : OffsetDateTime.now();
            }
            case "MoneyDeposited" -> availableBalance = availableBalance.add(amount);
            case "MoneyWithdrawn" -> availableBalance = availableBalance.subtract(amount);
            case "AccountClosed" -> status = "CLOSED";
            case "AccountKycUpdated" -> kycStatus = text(payload, "kycStatus");
            case "FundsReserved" -> {
                availableBalance = availableBalance.subtract(amount);
                reservedBalance = reservedBalance.add(amount);
            }
            case "FundsCaptured" -> reservedBalance = reservedBalance.subtract(amount);
            case "FundsReleased" -> {
                reservedBalance = reservedBalance.subtract(amount);
                availableBalance = availableBalance.add(amount);
            }
            case "TransferSourceReimbursed" -> availableBalance = availableBalance.add(amount);
            default -> {
                // Transfer saga markers, Loan* markers and unknown events do not
                // change the signed balance. They remain in the audit stream.
            }
        }
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    public AccountSnapshot snapshot() {
        return new AccountSnapshot(accountId, holderName, region,
                availableBalance, reservedBalance, status, kycStatus, openedAt, lastVersion);
    }

    public boolean exists() { return exists; }
    public UUID accountId() { return accountId; }
    public String holderName() { return holderName; }
    public String status() { return status; }
    public String region() { return region; }
    public String kycStatus() { return kycStatus; }
    public BigDecimal availableBalance() { return availableBalance; }
    public BigDecimal reservedBalance() { return reservedBalance; }
    public OffsetDateTime openedAt() { return openedAt; }
    public int lastVersion() { return lastVersion; }
    public void setLastVersion(int lastVersion) { this.lastVersion = lastVersion; }
}
