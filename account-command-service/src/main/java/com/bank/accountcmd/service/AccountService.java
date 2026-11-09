package com.bank.accountcmd.service;

import com.bank.accountcmd.config.ShardRouter;
import com.bank.accountcmd.domain.AccountExceptions;
import com.bank.accountcmd.domain.AccountSnapshot;
import com.bank.accountcmd.domain.AccountState;
import com.bank.accountcmd.event.AccountEventPublisher;
import com.bank.accountcmd.event.EventStore;
import com.bank.accountcmd.event.EventStore.Draft;
import com.bank.accountcmd.event.StoredEvent;
import com.bank.accountcmd.model.DepositCommand;
import com.bank.accountcmd.model.OpenAccountCommand;
import com.bank.accountcmd.model.TransferCommand;
import com.bank.accountcmd.model.TransferStatusView;
import com.bank.accountcmd.model.WithdrawCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Write-side account service: validates commands against the event-sourced aggregate,
 * appends hash-chained events to the account's regional shard, and publishes them.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final String LOCK_PREFIX = "account-lock:";

    private final EventStore eventStore;
    private final AccountEventPublisher publisher;
    private final ShardRouter router;
    private final RegionResolver regionResolver;
    private final IdempotencyStore idempotency;
    private final TransferSagaRepository sagas;
    private final RedissonClient redisson;
    private final ObjectMapper objectMapper;
    private final TaskExecutor sagaExecutor;
    private final long pauseAfterSourceMs;
    private final Counter commands;

    public AccountService(EventStore eventStore,
                          AccountEventPublisher publisher,
                          ShardRouter router,
                          RegionResolver regionResolver,
                          IdempotencyStore idempotency,
                          TransferSagaRepository sagas,
                          RedissonClient redisson,
                          ObjectMapper objectMapper,
                          @Qualifier("sagaExecutor") TaskExecutor sagaExecutor,
                          @Value("${app.saga.pause-after-source-ms:0}") long pauseAfterSourceMs,
                          MeterRegistry meterRegistry) {
        this.eventStore = eventStore;
        this.publisher = publisher;
        this.router = router;
        this.regionResolver = regionResolver;
        this.idempotency = idempotency;
        this.sagas = sagas;
        this.redisson = redisson;
        this.objectMapper = objectMapper;
        this.sagaExecutor = sagaExecutor;
        this.pauseAfterSourceMs = pauseAfterSourceMs;
        this.commands = meterRegistry.counter("bank.commands.applied");
    }

    // ------------------------------------------------------------------ open / lifecycle

    public AccountSnapshot open(OpenAccountCommand command, String idempotencyKey, String correlationId) {
        Optional<String> replay = idempotency.find(idempotencyKey);
        if (replay.isPresent()) {
            return parse(replay.get(), AccountSnapshot.class);
        }
        if (command.getHolderName() == null || command.getHolderName().isBlank()) {
            throw new AccountExceptions.BadRequest("holderName is required");
        }
        BigDecimal initial = command.getInitialDeposit() == null ? BigDecimal.ZERO : command.getInitialDeposit();
        validateAmountOrZero(initial, "initialDeposit");

        String region = regionResolver.resolve(command.getRegistrationIp());
        UUID accountId = command.getAccountId() == null ? UUID.randomUUID() : command.getAccountId();
        if (router.regionFor(accountId).isPresent()) {
            throw new AccountExceptions.Conflict("Account already exists: " + accountId);
        }

        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> opened = new LinkedHashMap<>();
        opened.put("holderName", command.getHolderName());
        opened.put("region", region);
        opened.put("registrationIp", command.getRegistrationIp());
        opened.put("kycStatus", "PENDING");
        opened.put("openedAt", now.toString());
        List<Draft> drafts = new ArrayList<>();
        drafts.add(new Draft("AccountOpened", opened));
        if (initial.signum() > 0) {
            drafts.add(new Draft("MoneyDeposited", amountPayload(initial, null, "INITIAL_DEPOSIT")));
        }

        for (int attempt = 0; ; attempt++) {
            try {
                router.tx(region).execute(status -> {
                    eventStore.appendManyWithin(status, accountId, region, drafts, correlationId);
                    router.jdbc(region).update("""
                                    INSERT INTO account_core(account_id, status, region, kyc_status, opened_at)
                                    VALUES (?, 'ACTIVE', ?, 'PENDING', ?)
                                    ON CONFLICT (account_id) DO UPDATE
                                      SET status = EXCLUDED.status, region = EXCLUDED.region,
                                          kyc_status = EXCLUDED.kyc_status, opened_at = EXCLUDED.opened_at
                                    """,
                            accountId, region, now);
                    router.jdbc(region).update("""
                                    INSERT INTO account_pii(account_id, holder_name, kyc_documents, registration_ip, address)
                                    VALUES (?, ?, '{}'::jsonb, ?, NULL)
                                    ON CONFLICT (account_id) DO UPDATE
                                      SET holder_name = EXCLUDED.holder_name,
                                          registration_ip = EXCLUDED.registration_ip
                                    """,
                            accountId, command.getHolderName(), command.getRegistrationIp());
                    return null;
                });
                break;
            } catch (DuplicateKeyException conflict) {
                if (attempt >= 1) {
                    throw new AccountExceptions.Conflict("Concurrent open for aggregate " + accountId);
                }
            }
        }
        router.register(accountId, region);
        commands.increment();

        AccountSnapshot snapshot = eventStore.fold(accountId).snapshot();
        idempotency.save(idempotencyKey, toJson(snapshot));
        return snapshot;
    }

    public AccountSnapshot getAccount(UUID accountId) {
        AccountState state = eventStore.fold(accountId);
        if (!state.exists()) {
            throw new AccountExceptions.NotFound("Account not found: " + accountId);
        }
        return state.snapshot();
    }

    public AccountSnapshot deposit(UUID accountId, BigDecimal amount, String deviceId,
                                   String idempotencyKey, String correlationId) {
        Optional<String> replay = idempotency.find(idempotencyKey);
        if (replay.isPresent()) {
            return parse(replay.get(), AccountSnapshot.class);
        }
        validateAmount(amount);
        AccountSnapshot snapshot = withLock(accountId, () -> {
            AccountState state = requireActive(accountId);
            String region = state.region();
            appendAndPublish(state.accountId(), region, List.of(
                    new Draft("MoneyDeposited", amountPayload(amount, deviceId, "DEPOSIT"))), correlationId);
            return eventStore.fold(accountId).snapshot();
        });
        idempotency.save(idempotencyKey, toJson(snapshot));
        commands.increment();
        return snapshot;
    }

    public AccountSnapshot withdraw(UUID accountId, BigDecimal amount, String deviceId,
                                    String idempotencyKey, String correlationId) {
        Optional<String> replay = idempotency.find(idempotencyKey);
        if (replay.isPresent()) {
            return parse(replay.get(), AccountSnapshot.class);
        }
        validateAmount(amount);
        AccountSnapshot snapshot = withLock(accountId, () -> {
            AccountState state = requireActive(accountId);
            if (state.availableBalance().compareTo(amount) < 0) {
                throw new AccountExceptions.Conflict(
                        "Insufficient funds: available " + state.availableBalance() + ", requested " + amount);
            }
            appendAndPublish(accountId, state.region(), List.of(
                    new Draft("MoneyWithdrawn", amountPayload(amount, deviceId, "WITHDRAWAL"))), correlationId);
            return eventStore.fold(accountId).snapshot();
        });
        idempotency.save(idempotencyKey, toJson(snapshot));
        commands.increment();
        return snapshot;
    }

    public AccountSnapshot close(UUID accountId, String idempotencyKey, String correlationId) {
        Optional<String> replay = idempotency.find(idempotencyKey);
        if (replay.isPresent()) {
            return parse(replay.get(), AccountSnapshot.class);
        }
        AccountSnapshot snapshot = withLock(accountId, () -> {
            AccountState state = requireActive(accountId);
            if (state.reservedBalance().signum() > 0) {
                throw new AccountExceptions.Conflict(
                        "Account has open reservations: " + state.reservedBalance());
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("closedAt", OffsetDateTime.now().toString());
            payload.put("reason", "CUSTOMER_REQUEST");
            appendAndPublish(accountId, state.region(),
                    List.of(new Draft("AccountClosed", payload)), correlationId);
            router.jdbc(state.region()).update(
                    "UPDATE account_core SET status = 'CLOSED' WHERE account_id = ?", accountId);
            return eventStore.fold(accountId).snapshot();
        });
        idempotency.save(idempotencyKey, toJson(snapshot));
        commands.increment();
        return snapshot;
    }

    public AccountSnapshot setKyc(UUID accountId, String kycStatus, String note,
                                  String idempotencyKey, String correlationId) {
        Optional<String> replay = idempotency.find(idempotencyKey);
        if (replay.isPresent()) {
            return parse(replay.get(), AccountSnapshot.class);
        }
        if (kycStatus == null || !List.of("PENDING", "VERIFIED", "REJECTED").contains(kycStatus)) {
            throw new AccountExceptions.BadRequest("kycStatus must be PENDING, VERIFIED or REJECTED");
        }
        AccountSnapshot snapshot = withLock(accountId, () -> {
            AccountState state = eventStore.fold(accountId);
            if (!state.exists()) {
                throw new AccountExceptions.NotFound("Account not found: " + accountId);
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("kycStatus", kycStatus);
            payload.put("note", note);
            appendAndPublish(accountId, state.region(),
                    List.of(new Draft("AccountKycUpdated", payload)), correlationId);
            router.jdbc(state.region()).update(
                    "UPDATE account_core SET kyc_status = ? WHERE account_id = ?", kycStatus, accountId);
            return eventStore.fold(accountId).snapshot();
        });
        idempotency.save(idempotencyKey, toJson(snapshot));
        commands.increment();
        return snapshot;
    }

    // ------------------------------------------------------------------ internal credit / debit

    public AccountSnapshot internalCredit(UUID accountId, BigDecimal amount, String reference,
                                          String deviceId, String correlationId) {
        validateAmount(amount);
        String key = reference == null ? null : "internal-credit:" + reference;
        Optional<String> replay = idempotency.find(key);
        if (replay.isPresent()) {
            return parse(replay.get(), AccountSnapshot.class);
        }
        withLock(accountId, () -> {
            AccountState state = eventStore.fold(accountId);
            if (!state.exists()) {
                throw new AccountExceptions.NotFound("Account not found: " + accountId);
            }
            appendAndPublish(accountId, state.region(), List.of(
                    new Draft("MoneyDeposited", amountPayload(amount, deviceId, reference))), correlationId);
            return null;
        });
        AccountSnapshot snapshot = eventStore.fold(accountId).snapshot();
        idempotency.save(key, toJson(snapshot));
        return snapshot;
    }

    public AccountSnapshot internalDebit(UUID accountId, BigDecimal amount, String reference,
                                         String deviceId, String correlationId) {
        validateAmount(amount);
        String key = reference == null ? null : "internal-debit:" + reference;
        Optional<String> replay = idempotency.find(key);
        if (replay.isPresent()) {
            return parse(replay.get(), AccountSnapshot.class);
        }
        AccountSnapshot snapshot = withLock(accountId, () -> {
            AccountState state = requireActive(accountId);
            if (state.availableBalance().compareTo(amount) < 0) {
                throw new AccountExceptions.Conflict("Insufficient funds");
            }
            appendAndPublish(accountId, state.region(), List.of(
                    new Draft("MoneyWithdrawn", amountPayload(amount, deviceId, reference))), correlationId);
            return eventStore.fold(accountId).snapshot();
        });
        idempotency.save(key, toJson(snapshot));
        return snapshot;
    }

    // ------------------------------------------------------------------ reservations

    public AccountSnapshot reserve(UUID accountId, UUID reservationId, BigDecimal amount,
                                   String correlationId) {
        validateAmount(amount);
        return withLock(accountId, () -> {
            AccountState state = eventStore.fold(accountId);
            if (!state.exists()) {
                throw new AccountExceptions.NotFound("Account not found: " + accountId);
            }
            if (hasEvent(state.region(), accountId, "FundsReserved", "reservationId", reservationId)) {
                return state.snapshot(); // reservation write is already durable
            }
            if (state.availableBalance().compareTo(amount) < 0) {
                throw new AccountExceptions.Conflict("Insufficient available balance to reserve");
            }
            Map<String, Object> payload = amountPayload(amount, null, "RESERVATION");
            payload.put("reservationId", reservationId.toString());
            appendAndPublish(accountId, state.region(),
                    List.of(new Draft("FundsReserved", payload)), correlationId);
            return eventStore.fold(accountId).snapshot();
        });
    }

    public AccountSnapshot captureReservation(UUID accountId, UUID reservationId, BigDecimal amount,
                                             String correlationId) {
        return withLock(accountId, () -> {
            AccountState state = eventStore.fold(accountId);
            if (!state.exists()) {
                throw new AccountExceptions.NotFound("Account not found: " + accountId);
            }
            if (hasEvent(state.region(), accountId, "FundsCaptured", "reservationId", reservationId)) {
                return state.snapshot();
            }
            Map<String, Object> payload = amountPayload(amount, null, "CAPTURE");
            payload.put("reservationId", reservationId.toString());
            appendAndPublish(accountId, state.region(),
                    List.of(new Draft("FundsCaptured", payload)), correlationId);
            return eventStore.fold(accountId).snapshot();
        });
    }

    public AccountSnapshot releaseReservation(UUID accountId, UUID reservationId, BigDecimal amount,
                                              String reason, String correlationId) {
        return withLock(accountId, () -> {
            AccountState state = eventStore.fold(accountId);
            if (!state.exists()) {
                throw new AccountExceptions.NotFound("Account not found: " + accountId);
            }
            if (hasEvent(state.region(), accountId, "FundsReleased", "reservationId", reservationId)) {
                return state.snapshot();
            }
            Map<String, Object> payload = amountPayload(amount, null, reason);
            payload.put("reservationId", reservationId.toString());
            payload.put("reason", reason);
            appendAndPublish(accountId, state.region(),
                    List.of(new Draft("FundsReleased", payload)), correlationId);
            return eventStore.fold(accountId).snapshot();
        });
    }

    // ------------------------------------------------------------------ transfer saga

    public record TransferResult(UUID transferId, String status) {
    }

    public TransferResult transfer(TransferCommand command, String idempotencyKey, String correlationId) {
        Optional<String> replay = idempotency.find(idempotencyKey);
        if (replay.isPresent()) {
            return parse(replay.get(), TransferResult.class);
        }
        if (command.getFromAccountId() == null || command.getToAccountId() == null) {
            throw new AccountExceptions.BadRequest("fromAccountId and toAccountId are required");
        }
        if (command.getFromAccountId().equals(command.getToAccountId())) {
            throw new AccountExceptions.BadRequest("Cannot transfer to the same account");
        }
        validateAmount(command.getAmount());
        router.regionOf(command.getFromAccountId());
        router.regionOf(command.getToAccountId());

        UUID transferId = UUID.randomUUID();
        sagas.create(transferId, command.getFromAccountId(), command.getToAccountId(), command.getAmount());
        String corr = correlationId == null ? transferId.toString() : correlationId;
        sagaExecutor.execute(() -> {
            try {
                executeSaga(transferId, corr);
            } catch (Exception e) {
                log.error("Transfer saga {} crashed: {}", transferId, e.getMessage(), e);
            }
        });
        TransferResult result = new TransferResult(transferId, TransferSagaRepository.INITIATED);
        idempotency.save(idempotencyKey, toJson(result));
        return result;
    }

    public TransferStatusView transferStatus(UUID transferId) {
        TransferSagaRepository.TransferSaga saga = sagas.find(transferId)
                .orElseThrow(() -> new AccountExceptions.NotFound("Transfer not found: " + transferId));
        TransferStatusView view = new TransferStatusView();
        view.setTransferId(saga.transferId());
        view.setFromAccountId(saga.fromAccountId());
        view.setToAccountId(saga.toAccountId());
        view.setAmount(saga.amount());
        view.setStatus(saga.status());
        view.setStartedAt(saga.startedAt());
        view.setUpdatedAt(saga.updatedAt());
        view.setFailureReason(saga.failureReason());
        return view;
    }

    private void executeSaga(UUID transferId, String correlationId) {
        TransferSagaRepository.TransferSaga saga = sagas.find(transferId).orElse(null);
        if (saga == null || saga.terminal()) {
            return;
        }
        String sourceRegion = router.regionOf(saga.fromAccountId());
        String targetRegion = router.regionOf(saga.toAccountId());

        try {
            // Step 1 — source debit (idempotent against a crash before the status write).
            if (!hasTransferEvent(sourceRegion, saga.fromAccountId(), "TransferSourceDebited", transferId)) {
                withLock(saga.fromAccountId(), () -> {
                    AccountState source = requireActive(saga.fromAccountId());
                    if (source.availableBalance().compareTo(saga.amount()) < 0) {
                        throw new AccountExceptions.Conflict("Insufficient funds for transfer");
                    }
                    Map<String, Object> initiated = sagaPayload(saga);
                    Map<String, Object> debit = amountPayload(saga.amount(), null, "TRANSFER_OUT");
                    debit.put("transferId", transferId.toString());
                    Map<String, Object> debited = new LinkedHashMap<>(debit);
                    appendAndPublish(saga.fromAccountId(), sourceRegion, List.of(
                            new Draft("TransferInitiated", initiated),
                            new Draft("MoneyWithdrawn", debit),
                            new Draft("TransferSourceDebited", debited)), correlationId);
                    return null;
                });
            }
            sagas.update(transferId, TransferSagaRepository.SOURCE_DEBITED, null);

            if (pauseAfterSourceMs > 0) {
                try {
                    Thread.sleep(pauseAfterSourceMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Step 2 — target credit. A closed/missing target fails the saga here.
            if (!hasTransferEvent(targetRegion, saga.toAccountId(), "TransferTargetCredited", transferId)) {
                withLock(saga.toAccountId(), () -> {
                    AccountState target = eventStore.fold(saga.toAccountId());
                    if (!target.exists()) {
                        throw new AccountExceptions.NotFound("Target account not found");
                    }
                    if (!"ACTIVE".equals(target.status())) {
                        throw new AccountExceptions.Conflict("Target account is " + target.status());
                    }
                    Map<String, Object> credit = amountPayload(saga.amount(), null, "TRANSFER_IN");
                    credit.put("transferId", transferId.toString());
                    Map<String, Object> credited = new LinkedHashMap<>(credit);
                    appendAndPublish(saga.toAccountId(), targetRegion, List.of(
                            new Draft("MoneyDeposited", credit),
                            new Draft("TransferTargetCredited", credited)), correlationId);
                    return null;
                });
            }
            sagas.update(transferId, TransferSagaRepository.TARGET_CREDITED, null);

            // Step 3 — completion marker in the source stream.
            appendAndPublish(saga.fromAccountId(), sourceRegion, List.of(
                    new Draft("TransferCompleted", Map.of("transferId", transferId.toString()))), correlationId);
            sagas.update(transferId, TransferSagaRepository.COMPLETED, null);
            log.info("Transfer {} completed", transferId);

        } catch (Exception failure) {
            compensate(saga, sourceRegion, failure.getMessage(), correlationId);
        }
    }

    private void compensate(TransferSagaRepository.TransferSaga saga, String sourceRegion,
                            String reason, String correlationId) {
        boolean sourceDebited = hasTransferEvent(sourceRegion, saga.fromAccountId(),
                "TransferSourceDebited", saga.transferId());
        String corr = correlationId == null ? saga.transferId().toString() : correlationId;
        if (sourceDebited) {
            Map<String, Object> failed = new LinkedHashMap<>();
            failed.put("transferId", saga.transferId().toString());
            failed.put("reason", reason);
            Map<String, Object> reimbursement = amountPayload(saga.amount(), null, "TRANSFER_REIMBURSEMENT");
            reimbursement.put("transferId", saga.transferId().toString());
            appendAndPublish(saga.fromAccountId(), sourceRegion, List.of(
                    new Draft("TransferFailed", failed),
                    new Draft("TransferSourceReimbursed", reimbursement)), corr);
            sagas.update(saga.transferId(), TransferSagaRepository.COMPENSATED, reason);
            log.warn("Transfer {} compensated: {}", saga.transferId(), reason);
        } else {
            Map<String, Object> failed = new LinkedHashMap<>();
            failed.put("transferId", saga.transferId().toString());
            failed.put("reason", reason);
            appendAndPublish(saga.fromAccountId(), sourceRegion,
                    List.of(new Draft("TransferFailed", failed)), corr);
            sagas.update(saga.transferId(), TransferSagaRepository.FAILED, reason);
            log.warn("Transfer {} failed before debit: {}", saga.transferId(), reason);
        }
    }

    /** Startup recovery: resume every non-terminal saga exactly where it stopped. */
    @Scheduled(initialDelay = 5_000, fixedDelay = 30_000)
    public void recoverSagas() {
        for (TransferSagaRepository.TransferSaga saga : sagas.findNonTerminal()) {
            log.info("Recovering transfer saga {} at status {}", saga.transferId(), saga.status());
            try {
                executeSaga(saga.transferId(), saga.transferId().toString());
            } catch (Exception e) {
                log.error("Recovery failed for saga {}: {}", saga.transferId(), e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ audit

    public EventStore.AuditVerification verifyAudit(UUID accountId) {
        return eventStore.verify(accountId);
    }

    // ------------------------------------------------------------------ helpers

    private AccountState requireActive(UUID accountId) {
        AccountState state = eventStore.fold(accountId);
        if (!state.exists()) {
            throw new AccountExceptions.NotFound("Account not found: " + accountId);
        }
        if (!"ACTIVE".equals(state.status())) {
            throw new AccountExceptions.Conflict("Account is " + state.status());
        }
        return state;
    }

    private void appendAndPublish(UUID accountId, String region, List<Draft> drafts, String correlationId) {
        List<StoredEvent> events = eventStore.appendMany(accountId, region, drafts, correlationId);
        events.forEach(publisher::publish);
    }

    private boolean hasEvent(String region, UUID accountId, String type, String field, UUID value) {
        return eventStore.loadFromShard(region, accountId).stream().anyMatch(event ->
                type.equals(event.eventType())
                        && value.toString().equals(String.valueOf(
                        eventStore.payloadOf(event).get(field))));
    }

    private boolean hasTransferEvent(String region, UUID accountId, String type, UUID transferId) {
        return hasEvent(region, accountId, type, "transferId", transferId);
    }

    private <T> T withLock(UUID accountId, Supplier<T> work) {
        RLock lock = redisson.getFairLock(LOCK_PREFIX + accountId);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(10, 30, TimeUnit.SECONDS);
            if (!acquired) {
                throw new AccountExceptions.Conflict("Could not acquire fair lock for account " + accountId);
            }
            return work.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AccountExceptions.Conflict("Interrupted while waiting for account lock");
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private static Map<String, Object> amountPayload(BigDecimal amount, String deviceId, String reference) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", amount);
        payload.put("currency", "INR");
        payload.put("deviceId", deviceId);
        payload.put("reference", reference);
        return payload;
    }

    private static Map<String, Object> sagaPayload(TransferSagaRepository.TransferSaga saga) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transferId", saga.transferId().toString());
        payload.put("fromAccountId", saga.fromAccountId().toString());
        payload.put("toAccountId", saga.toAccountId().toString());
        payload.put("amount", saga.amount());
        return payload;
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new AccountExceptions.BadRequest("amount must be greater than zero");
        }
        if (amount.scale() > 2) {
            throw new AccountExceptions.BadRequest("amount supports at most 2 decimal places");
        }
    }

    private static void validateAmountOrZero(BigDecimal amount, String field) {
        if (amount.signum() < 0) {
            throw new AccountExceptions.BadRequest(field + " cannot be negative");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialise response snapshot", e);
        }
    }

    private <T> T parse(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot replay idempotent response", e);
        }
    }
}
