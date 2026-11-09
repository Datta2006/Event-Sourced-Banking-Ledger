package com.bank.loan.service;

import com.bank.loan.client.AccountClient;
import com.bank.loan.event.LoanEventPublisher;
import com.bank.loan.model.LoanRequestCommand;
import com.bank.loan.model.LoanViews.AdminPoolView;
import com.bank.loan.model.LoanViews.LoanQueueEntry;
import com.bank.loan.model.LoanViews.LoanQueueView;
import com.bank.loan.model.LoanViews.LoanRequestView;
import com.bank.loan.model.LoanViews.PoolReplenishmentView;
import com.bank.loan.service.LoanRepository.LoanRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RScript;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loan lifecycle: priority queue, atomic pool debit, disbursement through the
 * account service, repayment and replenishment.
 *
 * <p>Queue score is {@code (3 - tier) * 10^13 + requestedAtEpochMillis}. The
 * published design sketch used {@code 10^9}, but that is smaller than a modern
 * epoch-millis value, so tier could never outrank arrival. {@code 10^13} keeps
 * the documented intent: trust tier first, FCFS within a tier.</p>
 */
@Service
public class LoanService {

    private static final Logger log = LoggerFactory.getLogger(LoanService.class);
    private static final String QUEUE_KEY = "loan:queue";
    private static final String POOL_KEY = "admin_pool:balance";
    private static final UUID POOL_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private static final String DEBIT_SCRIPT = """
            local force = ARGV[3] == '1'
            local head = redis.call('ZRANGE', KEYS[1], 0, 0)[1]
            if not force then
              if not head then return '-2' end
              if head ~= ARGV[1] then return '-3' end
            end
            local bal = tonumber(redis.call('GET', KEYS[2]) or '0')
            local amount = tonumber(ARGV[2])
            if bal < amount then return '-1' end
            redis.call('ZREM', KEYS[1], ARGV[1])
            local newbal = redis.call('INCRBYFLOAT', KEYS[2], '-'..ARGV[2])
            return tostring(newbal)
            """;

    private static final String REPLENISH_SCRIPT = """
            local newbal = redis.call('INCRBYFLOAT', KEYS[1], ARGV[1])
            return tostring(newbal)
            """;

    private final LoanRepository repository;
    private final IdempotencyStore idempotency;
    private final RedissonClient redisson;
    private final LoanEventPublisher publisher;
    private final AccountClient accountClient;
    private final ObjectMapper objectMapper;
    private final TaskExecutor workerExecutor;
    private final BigDecimal initialPool;
    private final int termDays;
    private final int earlyDays;
    private final AtomicBoolean draining = new AtomicBoolean(false);

    public LoanService(LoanRepository repository,
                       IdempotencyStore idempotency,
                       RedissonClient redisson,
                       LoanEventPublisher publisher,
                       AccountClient accountClient,
                       ObjectMapper objectMapper,
                       @Qualifier("loanWorker") TaskExecutor workerExecutor,
                       @Value("${app.pool.initial-balance:50000.00}") BigDecimal initialPool,
                       @Value("${app.loan.term-days:30}") int termDays,
                       @Value("${app.loan.early-days:7}") int earlyDays) {
        this.repository = repository;
        this.idempotency = idempotency;
        this.redisson = redisson;
        this.publisher = publisher;
        this.accountClient = accountClient;
        this.objectMapper = objectMapper;
        this.workerExecutor = workerExecutor;
        this.initialPool = initialPool;
        this.termDays = termDays;
        this.earlyDays = earlyDays;
    }

    @PostConstruct
    void initializePool() {
        repository.ensurePool(POOL_ID, initialPool);
        RBucket<String> bucket = poolBucket();
        String current = bucket.get();
        if (current == null) {
            BigDecimal db = repository.poolBalance();
            bucket.set(db.toPlainString());
            log.info("Initialised admin pool at {}", db);
        } else {
            BigDecimal redisBalance = new BigDecimal(current);
            repository.updatePool(redisBalance);
            log.info("Reconciled admin pool from Valkey: {}", redisBalance);
        }
    }

    // ------------------------------------------------------------------ requests

    public LoanRequestView request(LoanRequestCommand command, String idempotencyKey,
                                   String correlationId) {
        Optional<LoanRequestView> replay = replay(idempotencyKey);
        if (replay.isPresent()) {
            return replay.get();
        }
        if (command.getAccountId() == null) {
            throw new LoanExceptions.BadRequest("accountId is required");
        }
        validateAmount(command.getAmount());
        int tier = repository.trustTier(command.getAccountId());
        OffsetDateTime now = OffsetDateTime.now();
        LoanRow row = new LoanRow(UUID.randomUUID(), command.getAccountId(), command.getAmount(),
                command.getPurpose(), tier, "QUEUED", now, null, null, null, null);
        repository.insert(row);
        queue().add(score(tier, now), row.loanId().toString());

        LoanRequestView view = row.view();
        remember(idempotencyKey, view);
        workerExecutor.execute(this::drainQueue);
        return view;
    }

    public List<LoanRequestView> list(UUID accountId, String status) {
        return repository.list(accountId, status == null || status.isBlank() ? null : status)
                .stream().map(LoanRow::view).toList();
    }

    public LoanRequestView get(UUID loanId) {
        return find(loanId).view();
    }

    // ------------------------------------------------------------------ queue / pool

    @Scheduled(fixedDelayString = "${app.loan.worker-delay-ms:2000}", initialDelay = 3000)
    public void drainQueue() {
        if (!draining.compareAndSet(false, true)) {
            return;
        }
        try {
            for (int i = 0; i < 20; i++) {
                String head = queue().first();
                if (head == null) {
                    return;
                }
                UUID loanId;
                try {
                    loanId = UUID.fromString(head);
                } catch (RuntimeException e) {
                    queue().remove(head);
                    continue;
                }
                LoanRow row = repository.find(loanId).orElse(null);
                if (row == null || !List.of("QUEUED", "WAITING_FOR_FUNDS").contains(row.status())) {
                    queue().remove(head);
                    continue;
                }
                String result = debitPool(loanId, row.amount(), false);
                if ("-1".equals(result)) {
                    repository.updateStatus(loanId, "WAITING_FOR_FUNDS",
                            "Insufficient admin pool");
                    return; // head-of-line: nobody behind a higher-tier loan can jump it
                }
                if ("-2".equals(result) || "-3".equals(result)) {
                    continue;
                }
                try {
                    completeDisbursement(row, new BigDecimal(result), "queue-worker");
                } catch (RuntimeException e) {
                    log.warn("Disbursement of {} failed: {}", loanId, e.getMessage());
                    return;
                }
            }
        } finally {
            draining.set(false);
        }
    }

    public LoanQueueView queueView() {
        Collection<String> ids = queue().valueRange(0, -1);
        List<LoanQueueEntry> entries = new ArrayList<>();
        int position = 1;
        for (String id : ids) {
            UUID loanId;
            try {
                loanId = UUID.fromString(id);
            } catch (RuntimeException e) {
                continue;
            }
            LoanRow row = repository.find(loanId).orElse(null);
            if (row == null || !List.of("QUEUED", "WAITING_FOR_FUNDS").contains(row.status())) {
                continue;
            }
            entries.add(new LoanQueueEntry(row.loanId(), row.accountId(), row.amount(),
                    row.trustTier(), row.status(), position++, row.requestedAt()));
        }
        return new LoanQueueView(entries, OffsetDateTime.now());
    }

    public AdminPoolView pool() {
        return new AdminPoolView(readPool(), "INR", OffsetDateTime.now());
    }

    public AdminPoolView replenish(BigDecimal amount, String correlationId) {
        validateAmount(amount);
        Replenishment result = creditPool(amount, "ADMIN_DEPOSIT", correlationId);
        workerExecutor.execute(this::drainQueue);
        return new AdminPoolView(result.balanceAfter(), "INR", result.at());
    }

    public List<PoolReplenishmentView> poolHistory() {
        return repository.replenishments().stream()
                .map(row -> new PoolReplenishmentView(row.eventId(), row.amount(),
                        row.balanceAfter(), row.at()))
                .toList();
    }

    // ------------------------------------------------------------------ decisions / repayment

    public LoanRequestView decide(UUID loanId, String decision, String note,
                                  String idempotencyKey, String correlationId) {
        Optional<LoanRequestView> replay = replay(idempotencyKey);
        if (replay.isPresent()) {
            return replay.get();
        }
        withLock(loanId, () -> {
            LoanRow row = find(loanId);
            if (!List.of("QUEUED", "WAITING_FOR_FUNDS").contains(row.status())) {
                throw new LoanExceptions.Conflict("Loan is already " + row.status());
            }
            String normalised = decision == null ? "" : decision.trim().toUpperCase();
            if ("REJECT".equals(normalised)) {
                queue().remove(loanId.toString());
                repository.updateStatus(loanId, "REJECTED", note);
                publisher.publish(row.accountId(), "LoanRejected",
                        payload("loanId", loanId, "accountId", row.accountId(),
                                "reason", note == null ? "BANK_REJECTED" : note), correlationId);
                return;
            }
            if (!"APPROVE".equals(normalised)) {
                throw new LoanExceptions.BadRequest("decision must be APPROVE or REJECT");
            }
            String result = debitPool(loanId, row.amount(), true);
            if ("-1".equals(result)) {
                throw new LoanExceptions.Conflict("Admin pool cannot cover this loan");
            }
            if (result.startsWith("-")) {
                throw new LoanExceptions.Conflict("Loan is no longer in the queue");
            }
            completeDisbursement(row, new BigDecimal(result), correlationId);
        });
        LoanRequestView view = get(loanId);
        remember(idempotencyKey, view);
        return view;
    }

    public LoanRequestView repay(UUID loanId, BigDecimal amount, String idempotencyKey,
                                 String correlationId) {
        Optional<LoanRequestView> replay = replay(idempotencyKey);
        if (replay.isPresent()) {
            return replay.get();
        }
        validateAmount(amount);
        withLock(loanId, () -> {
            LoanRow row = find(loanId);
            if (!"DISBURSED".equals(row.status())) {
                throw new LoanExceptions.Conflict("Loan is " + row.status() + ", not DISBURSED");
            }
            if (amount.compareTo(row.amount()) != 0) {
                throw new LoanExceptions.BadRequest(
                        "Full repayment only: expected " + row.amount());
            }
            try {
                accountClient.debit(row.accountId(), amount,
                        "LOAN_REPAYMENT:" + loanId, correlationId);
            } catch (AccountClient.AccountCallException e) {
                throw new LoanExceptions.Upstream("Account debit failed: " + e.getMessage());
            }
            OffsetDateTime now = OffsetDateTime.now();
            repository.markRepaid(loanId, now);

            String eventType = repaymentType(row, now);
            publisher.publish(row.accountId(), eventType,
                    payload("loanId", loanId, "accountId", row.accountId(),
                            "amount", amount, "repaidAt", now.toString()), correlationId);
            Replenishment replenishment = creditPool(amount, "LOAN_REPAID", correlationId);
            publisher.publish(row.accountId(), "AdminPoolReplenished",
                    payload("poolId", POOL_ID, "amount", amount, "reason", "LOAN_REPAID",
                            "balanceAfter", replenishment.balanceAfter(),
                            "at", replenishment.at().toString()), correlationId);
        });
        LoanRequestView view = get(loanId);
        remember(idempotencyKey, view);
        return view;
    }

    // ------------------------------------------------------------------ internals

    private void completeDisbursement(LoanRow row, BigDecimal newPoolBalance, String correlationId) {
        try {
            accountClient.credit(row.accountId(), row.amount(),
                    "LOAN_DISBURSEMENT:" + row.loanId(), correlationId);
        } catch (AccountClient.AccountCallException e) {
            // Put the money back and requeue: the loan is not disbursed.
            BigDecimal restored = creditPoolRaw(row.amount());
            repository.updatePool(restored);
            repository.updateStatus(row.loanId(), "WAITING_FOR_FUNDS",
                    "Account credit failed: " + e.getMessage());
            queue().add(score(row.trustTier(), row.requestedAt()), row.loanId().toString());
            throw new LoanExceptions.Upstream("Account credit failed: " + e.getMessage());
        }
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime due = now.plusDays(termDays);
        repository.markDisbursed(row.loanId(), now, due);
        repository.updatePool(newPoolBalance);

        publisher.publish(row.accountId(), "LoanApproved",
                payload("loanId", row.loanId(), "accountId", row.accountId(),
                        "amount", row.amount(), "trustTier", row.trustTier()), correlationId);
        publisher.publish(row.accountId(), "LoanDisbursed",
                payload("loanId", row.loanId(), "accountId", row.accountId(),
                        "amount", row.amount(), "disbursedAt", now.toString()),
                correlationId);
        log.info("Loan {} disbursed to {}", row.loanId(), row.accountId());
    }

    private String repaymentType(LoanRow row, OffsetDateTime repaidAt) {
        OffsetDateTime disbursed = row.disbursedAt() == null ? repaidAt : row.disbursedAt();
        if (repaidAt.isBefore(disbursed.plusDays(earlyDays))) {
            return "LoanRepaidEarly";
        }
        OffsetDateTime due = row.dueAt() == null ? disbursed.plusDays(termDays) : row.dueAt();
        return repaidAt.isAfter(due) ? "LoanRepaidLate" : "LoanRepaidOnTime";
    }

    private record Replenishment(UUID eventId, BigDecimal balanceAfter, OffsetDateTime at) {
    }

    private Replenishment creditPool(BigDecimal amount, String reason, String correlationId) {
        Replenishment result = creditPoolInternal(amount);
        repository.recordReplenishment(result.eventId(), amount, result.balanceAfter(), reason, result.at());
        repository.updatePool(result.balanceAfter());
        publisher.publish(POOL_ID, "AdminPoolReplenished",
                payload("poolId", POOL_ID, "amount", amount, "reason", reason,
                        "balanceAfter", result.balanceAfter(), "at", result.at().toString()),
                correlationId);
        return result;
    }

    private Replenishment creditPoolInternal(BigDecimal amount) {
        String newBalance = replenishPoolRaw(amount);
        return new Replenishment(UUID.randomUUID(), new BigDecimal(newBalance), OffsetDateTime.now());
    }

    private BigDecimal creditPoolRaw(BigDecimal amount) {
        return new BigDecimal(replenishPoolRaw(amount));
    }

    private String replenishPoolRaw(BigDecimal amount) {
        RScript script = redisson.getScript(StringCodec.INSTANCE);
        String result = script.eval(RScript.Mode.READ_WRITE, REPLENISH_SCRIPT,
                RScript.ReturnType.STRING, List.of(POOL_KEY), amount.toPlainString());
        return result == null ? "0" : result;
    }

    private String debitPool(UUID loanId, BigDecimal amount, boolean force) {
        RScript script = redisson.getScript(StringCodec.INSTANCE);
        String result = script.eval(RScript.Mode.READ_WRITE, DEBIT_SCRIPT,
                RScript.ReturnType.STRING, List.of(QUEUE_KEY, POOL_KEY),
                loanId.toString(), amount.toPlainString(), force ? "1" : "0");
        return result == null ? "-2" : result;
    }

    private BigDecimal readPool() {
        String value = poolBucket().get();
        if (value == null) {
            BigDecimal db = repository.poolBalance();
            poolBucket().set(db.toPlainString());
            return db;
        }
        return new BigDecimal(value);
    }

    private RBucket<String> poolBucket() {
        return redisson.getBucket(POOL_KEY, StringCodec.INSTANCE);
    }

    private RScoredSortedSet<String> queue() {
        return redisson.getScoredSortedSet(QUEUE_KEY, StringCodec.INSTANCE);
    }

    private static double score(int tier, OffsetDateTime requestedAt) {
        return (3 - tier) * 10_000_000_000_000.0
                + requestedAt.toInstant().toEpochMilli();
    }

    private LoanRow find(UUID loanId) {
        return repository.find(loanId)
                .orElseThrow(() -> new LoanExceptions.NotFound("Loan not found: " + loanId));
    }

    private void withLock(UUID loanId, Runnable work) {
        RLock lock = redisson.getFairLock("loan-lock:" + loanId);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(10, 30, TimeUnit.SECONDS);
            if (!acquired) {
                throw new LoanExceptions.Conflict("Could not lock loan " + loanId);
            }
            work.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LoanExceptions.Conflict("Interrupted while locking loan " + loanId);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new LoanExceptions.BadRequest("amount must be greater than zero");
        }
        if (amount.scale() > 2) {
            throw new LoanExceptions.BadRequest("amount supports at most 2 decimal places");
        }
    }

    private static Map<String, Object> payload(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    private Optional<LoanRequestView> replay(String key) {
        return idempotency.find(key).map(json -> {
            try {
                return objectMapper.readValue(json, LoanRequestView.class);
            } catch (Exception e) {
                throw new IllegalStateException("Cannot replay loan response", e);
            }
        });
    }

    private void remember(String key, LoanRequestView view) {
        try {
            idempotency.save(key, objectMapper.writeValueAsString(view));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot store idempotent loan response", e);
        }
    }

    /** Domain failures mapped to HTTP by the controller advice. */
    public static final class LoanExceptions {
        private LoanExceptions() {
        }

        public static class NotFound extends RuntimeException {
            public NotFound(String message) { super(message); }
        }

        public static class Conflict extends RuntimeException {
            public Conflict(String message) { super(message); }
        }

        public static class BadRequest extends RuntimeException {
            public BadRequest(String message) { super(message); }
        }

        public static class Upstream extends RuntimeException {
            public Upstream(String message) { super(message); }
        }
    }
}
