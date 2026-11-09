package com.bank.accountcmd.controller;

import com.bank.accountcmd.domain.AccountSnapshot;
import com.bank.accountcmd.event.EventStore;
import com.bank.accountcmd.model.DepositCommand;
import com.bank.accountcmd.model.OpenAccountCommand;
import com.bank.accountcmd.model.TransferCommand;
import com.bank.accountcmd.model.TransferStatusView;
import com.bank.accountcmd.model.WithdrawCommand;
import com.bank.accountcmd.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Write-side REST API (docs/api_spec/account-command-service.yaml).
 *
 * <p>The gateway strips {@code /api}, so controllers are mounted at the bare
 * resource paths. {@code /internal/**} is service-to-service only: the gateway
 * must not route it, and callers identify themselves with the usual injected
 * caller headers.</p>
 */
@RestController
@RequestMapping
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // ------------------------------------------------------------------ account lifecycle

    @PostMapping("/accounts")
    public ResponseEntity<AccountSnapshot> open(
            @RequestBody OpenAccountCommand command,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        String key = idempotency(command.getIdempotencyKey(), idempotencyKey);
        AccountSnapshot snapshot = accountService.open(command, key, correlation(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(snapshot);
    }

    @GetMapping("/accounts/{accountId}")
    public AccountSnapshot get(@PathVariable UUID accountId) {
        return accountService.getAccount(accountId);
    }

    @PostMapping("/accounts/{accountId}/deposits")
    public ResponseEntity<AccountSnapshot> deposit(
            @PathVariable UUID accountId,
            @RequestBody DepositCommand command,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        AccountSnapshot snapshot = accountService.deposit(accountId, command.getAmount(),
                command.getDeviceId(), idempotency(command.getIdempotencyKey(), idempotencyKey),
                correlation(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(snapshot);
    }

    @PostMapping("/accounts/{accountId}/withdrawals")
    public ResponseEntity<AccountSnapshot> withdraw(
            @PathVariable UUID accountId,
            @RequestBody WithdrawCommand command,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        AccountSnapshot snapshot = accountService.withdraw(accountId, command.getAmount(),
                command.getDeviceId(), idempotency(command.getIdempotencyKey(), idempotencyKey),
                correlation(request));
        // The command is applied synchronously; 202 preserves the published contract
        // for the ordered-command path and still returns the resulting snapshot.
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(snapshot);
    }

    @PostMapping("/accounts/{accountId}/close")
    public AccountSnapshot close(
            @PathVariable UUID accountId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        return accountService.close(accountId, idempotency(null, idempotencyKey), correlation(request));
    }

    @PostMapping("/accounts/{accountId}/kyc")
    public AccountSnapshot setKyc(
            @PathVariable UUID accountId,
            @RequestBody KycRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        return accountService.setKyc(accountId, body.kycStatus(), body.note(),
                idempotency(null, idempotencyKey), correlation(request));
    }

    @GetMapping("/accounts/{accountId}/audit/verify")
    public EventStore.AuditVerification verifyAudit(@PathVariable UUID accountId) {
        return accountService.verifyAudit(accountId);
    }

    // ------------------------------------------------------------------ transfers

    @PostMapping("/transfers")
    public ResponseEntity<AccountService.TransferResult> transfer(
            @RequestBody TransferCommand command,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        AccountService.TransferResult result = accountService.transfer(command,
                idempotency(command.getIdempotencyKey(), idempotencyKey), correlation(request));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @GetMapping("/transfers/{transferId}")
    public TransferStatusView transferStatus(@PathVariable UUID transferId) {
        return accountService.transferStatus(transferId);
    }

    // ------------------------------------------------------------------ internal (service-to-service)

    @PostMapping("/internal/accounts/{accountId}/credit")
    public AccountSnapshot internalCredit(
            @PathVariable UUID accountId,
            @RequestBody InternalFundsRequest body,
            HttpServletRequest request) {
        return accountService.internalCredit(accountId, body.amount(), body.reference(),
                body.deviceId(), correlation(request));
    }

    @PostMapping("/internal/accounts/{accountId}/debit")
    public AccountSnapshot internalDebit(
            @PathVariable UUID accountId,
            @RequestBody InternalFundsRequest body,
            HttpServletRequest request) {
        return accountService.internalDebit(accountId, body.amount(), body.reference(),
                body.deviceId(), correlation(request));
    }

    @PostMapping("/internal/accounts/{accountId}/reservations")
    public AccountSnapshot reserve(
            @PathVariable UUID accountId,
            @RequestBody ReserveRequest body,
            HttpServletRequest request) {
        return accountService.reserve(accountId, body.reservationId(), body.amount(),
                correlation(request));
    }

    @PostMapping("/internal/accounts/{accountId}/reservations/{reservationId}/capture")
    public AccountSnapshot capture(
            @PathVariable UUID accountId,
            @PathVariable UUID reservationId,
            @RequestBody(required = false) CaptureRequest body,
            HttpServletRequest request) {
        BigDecimal amount = body == null || body.amount() == null
                ? BigDecimal.ZERO : body.amount();
        return accountService.captureReservation(accountId, reservationId, amount, correlation(request));
    }

    @PostMapping("/internal/accounts/{accountId}/reservations/{reservationId}/release")
    public AccountSnapshot release(
            @PathVariable UUID accountId,
            @PathVariable UUID reservationId,
            @RequestBody(required = false) ReleaseRequest body,
            HttpServletRequest request) {
        BigDecimal amount = body == null || body.amount() == null
                ? BigDecimal.ZERO : body.amount();
        String reason = body == null || body.reason() == null ? "RELEASED" : body.reason();
        return accountService.releaseReservation(accountId, reservationId, amount, reason,
                correlation(request));
    }

    @GetMapping("/internal/accounts/{accountId}")
    public AccountSnapshot internalGet(@PathVariable UUID accountId) {
        return accountService.getAccount(accountId);
    }

    // ------------------------------------------------------------------ helpers

    private static String correlation(HttpServletRequest request) {
        return request.getHeader("X-Correlation-Id");
    }

    /**
     * Cross-service calls are not retried by a user, so derive a deterministic
     * idempotency key from the business reference when no header is supplied.
     */
    private static String idempotency(String bodyKey, String headerKey) {
        if (headerKey != null && !headerKey.isBlank()) {
            return headerKey;
        }
        if (bodyKey != null && !bodyKey.isBlank()) {
            return bodyKey;
        }
        return null;
    }

    public record KycRequest(String kycStatus, String note) {
    }

    public record InternalFundsRequest(BigDecimal amount, String reference, String deviceId) {
    }

    public record ReserveRequest(UUID reservationId, BigDecimal amount) {
    }

    public record CaptureRequest(BigDecimal amount) {
    }

    public record ReleaseRequest(BigDecimal amount, String reason) {
    }
}
