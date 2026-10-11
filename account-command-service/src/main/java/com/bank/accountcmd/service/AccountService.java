package com.bank.accountcmd.service;

import com.bank.accountcmd.model.DepositCommand;
import com.bank.accountcmd.model.TransferCommand;
import com.bank.accountcmd.model.WithdrawCommand;
import com.bank.accountcmd.persistence.AccountCore;
import com.bank.accountcmd.persistence.AccountCoreRepository;
import com.bank.accountcmd.persistence.AccountPii;
import com.bank.accountcmd.persistence.AccountPiiRepository;
import com.bank.accountcmd.persistence.LedgerEvent;
import com.bank.accountcmd.persistence.LedgerEventRepository;
import com.bank.accountcmd.persistence.ProcessedRequest;
import com.bank.accountcmd.persistence.ProcessedRequestRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Service handling account operations with proper locking and event sourcing.
 * Implements requirements from HLD_LLD_SystemDesign_v2.md:
 * - Optimistic concurrency via UNIQUE(aggregate_id, version) on ledger_events
 * - Idempotency keys on every state-changing API call
 * - Redisson RFairLock for fair locking around withdrawals/deposits/transfers
 * - Kafka publishing for events
 */
@Service
public class AccountService {

    @Autowired
    private LedgerEventRepository ledgerEventRepository;

    @Autowired
    private AccountCoreRepository accountCoreRepository;

    @Autowired
    private AccountPiiRepository accountPiiRepository;

    @Autowired
    private ProcessedRequestRepository processedRequestRepository;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private static final String ACCOUNT_LOCK_PREFIX = "account-lock:";

    /**
     * Process a deposit command with idempotency check and fair locking.
     */
    @Transactional
    public void processDeposit(DepositCommand command) {
        // Check idempotency first
        if (isDuplicateRequest(command.getIdempotencyKey())) {
            // Replay the original result instead of double-processing
            return;
        }

        // Acquire fair lock for the account
        RLock lock = redissonClient.getFairLock(ACCOUNT_LOCK_PREFIX + command.getAccountId());
        lock.lock();
        try {
            // Get or create account core
            AccountCore accountCore = accountCoreRepository.findById(command.getAccountId())
                    .orElseGet(() -> {
                        AccountCore newAccount = new AccountCore();
                        newAccount.setAccountId(command.getAccountId());
                        newAccount.setStatus("ACTIVE");
                        newAccount.setOpenedAt(OffsetDateTime.now());
                        return accountCoreRepository.save(newAccount);
                    });

            // Append MoneyDeposited event to ledger_events
            LedgerEvent depositEvent = createLedgerEvent(
                    command.getAccountId(),
                    "MoneyDeposited",
                    createDepositPayload(command.getAmount())
            );
            ledgerEventRepository.save(depositEvent);

            // Publish to Kafka
            kafkaTemplate.send("account.events", command.getAccountId().toString(), depositEvent);

            // Mark request as processed
            markProcessed(command.getIdempotencyKey(), depositEvent);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Process a withdraw command with idempotency check and fair locking.
     */
    @Transactional
    public void processWithdraw(WithdrawCommand command) {
        // Check idempotency first
        if (isDuplicateRequest(command.getIdempotencyKey())) {
            // Replay the original result instead of double-processing
            return;
        }

        // Acquire fair lock for the account
        RLock lock = redissonClient.getFairLock(ACCOUNT_LOCK_PREFIX + command.getAccountId());
        lock.lock();
        try {
            // Check balance and account status
            AccountCore accountCore = accountCoreRepository.findById(command.getAccountId())
                    .orElseThrow(() -> new IllegalStateException("Account not found"));

            if (!"ACTIVE".equals(accountCore.getStatus())) {
                throw new IllegalStateException("Account is not active");
            }

            // TODO: In a real implementation, we would check the current balance
            // by replaying events or querying a read model. For now, we'll assume
            // sufficient funds for the scaffold.

            // Append MoneyWithdrawn event to ledger_events
            LedgerEvent withdrawEvent = createLedgerEvent(
                    command.getAccountId(),
                    "MoneyWithdrawn",
                    createWithdrawPayload(command.getAmount())
            );
            ledgerEventRepository.save(withdrawEvent);

            // Publish to Kafka
            kafkaTemplate.send("account.events", command.getAccountId().toString(), withdrawEvent);

            // Mark request as processed
            markProcessed(command.getIdempotencyKey(), withdrawEvent);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Process a transfer command with idempotency check and fair locking on both accounts.
     * Locks are acquired in a consistent order to prevent deadlocks.
     */
    @Transactional
    public void processTransfer(TransferCommand command) {
        // Check idempotency first
        if (isDuplicateRequest(command.getIdempotencyKey())) {
            // Replay the original result instead of double-processing
            return;
        }

        // Determine lock order to prevent deadlocks (lower UUID first)
        UUID firstAccountId = command.getFromAccountId();
        UUID secondAccountId = command.getToAccountId();
        
        if (firstAccountId.compareTo(secondAccountId) > 0) {
            // Swap to ensure consistent ordering
            UUID temp = firstAccountId;
            firstAccountId = secondAccountId;
            secondAccountId = temp;
        }

        // Acquire fair locks in consistent order
        RLock firstLock = redissonClient.getFairLock(ACCOUNT_LOCK_PREFIX + firstAccountId);
        RLock secondLock = redissonClient.getFairLock(ACCOUNT_LOCK_PREFIX + secondAccountId);
        
        firstLock.lock();
        try {
            secondLock.lock();
            try {
                // Check source account
                AccountCore sourceAccount = accountCoreRepository.findById(command.getFromAccountId())
                        .orElseThrow(() -> new IllegalStateException("Source account not found"));
                        
                if (!"ACTIVE".equals(sourceAccount.getStatus())) {
                    throw new IllegalStateException("Source account is not active");
                }

                // Check destination account
                AccountCore destAccount = accountCoreRepository.findById(command.getToAccountId())
                        .orElseThrow(() -> new IllegalStateException("Destination account not found"));
                        
                if (!"ACTIVE".equals(destAccount.getStatus())) {
                    throw new IllegalStateException("Destination account is not active");
                }

                // TODO: In a real implementation, we would check source balance
                // by replaying events or querying a read model. For now, we'll assume
                // sufficient funds for the scaffold.

                // Append MoneyWithdrawn event for source account
                LedgerEvent withdrawEvent = createLedgerEvent(
                        command.getFromAccountId(),
                        "MoneyWithdrawn",
                        createTransferWithdrawPayload(command.getAmount())
                );
                ledgerEventRepository.save(withdrawEvent);

                // Append MoneyDeposited event for destination account
                LedgerEvent depositEvent = createLedgerEvent(
                        command.getToAccountId(),
                        "MoneyDeposited",
                        createTransferDepositPayload(command.getAmount())
                );
                ledgerEventRepository.save(depositEvent);

                // Publish both events to Kafka
                kafkaTemplate.send("account.events", command.getFromAccountId().toString(), withdrawEvent);
                kafkaTemplate.send("account.events", command.getToAccountId().toString(), depositEvent);

                // Mark request as processed
                markProcessed(command.getIdempotencyKey(), withdrawEvent);
            } finally {
                secondLock.unlock();
            }
        } finally {
            firstLock.unlock();
        }
    }

    /**
     * Create a ledger event with proper versioning.
     */
    private LedgerEvent createLedgerEvent(UUID accountId, String eventType, String payload) {
        // Get the current version for this account (optimistic concurrency)
        int version = getNextVersion(accountId);
        
        LedgerEvent event = new LedgerEvent();
        event.setEventId(UUID.randomUUID());
        event.setAggregateId(accountId);
        event.setVersion(version);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setOccurredAt(OffsetDateTime.now());
        // Region will be set during ShardingSphere retrofit (v2 §9 step 6)
        return event;
    }

    /**
     * Get the next version for an account by finding the max version and adding 1.
     * This implements the optimistic concurrency pattern.
     */
    private int getNextVersion(UUID accountId) {
        Integer maxVersion = ledgerEventRepository.findMaxVersionByAggregateId(accountId);
        return (maxVersion == null) ? 0 : maxVersion + 1;
    }

    /**
     * Check if a request with this idempotency key has already been processed.
     */
    private boolean isDuplicateRequest(String idempotencyKey) {
        return processedRequestRepository.findById(idempotencyKey).isPresent();
    }

    /**
     * Mark a request as processed for idempotency.
     */
    private void markProcessed(String idempotencyKey, LedgerEvent event) {
        ProcessedRequest processedRequest = new ProcessedRequest();
        processedRequest.setKey(idempotencyKey);
        // In a real implementation, we would serialize the actual response
        // For now, we'll store a simple snapshot
        processedRequest.setResponseSnapshot("{\"status\": \"PROCESSED\"}");
        processedRequest.setExpiresAt(OffsetDateTime.now().plusHours(24)); // 24h expiry
        processedRequestRepository.save(processedRequest);
    }

    /**
     * Create JSON payload for deposit event.
     */
    private String createDepositPayload(BigDecimal amount) {
        return String.format("{\"amount\": %s, \"currency\": \"INR\"}", amount);
    }

    /**
     * Create JSON payload for withdrawal event.
     */
    private String createWithdrawPayload(BigDecimal amount) {
        return String.format("{\"amount\": %s, \"currency\": \"INR\"}", amount);
    }

    /**
     * Create JSON payload for transfer withdrawal event.
     */
    private String createTransferWithdrawPayload(BigDecimal amount) {
        return String.format("{\"amount\": %s, \"currency\": \"INR\", \"type\": \"TRANSFER_OUT\"}", amount);
    }

    /**
     * Create JSON payload for transfer deposit event.
     */
    private String createTransferDepositPayload(BigDecimal amount) {
        return String.format("{\"amount\": %s, \"currency\": \"INR\", \"type\": \"TRANSFER_IN\"}", amount);
    }

    /**
     * Get account information (simplified for scaffolding).
     * In a real implementation, this would query a read model.
     */
    public AccountResponse getAccountInfo(UUID accountId) {
        AccountCore accountCore = accountCoreRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Account not found"));
        
        // In a real system, available balance would come from projecting events
        // For now, we'll return a placeholder
        return new AccountResponse(
                accountCore.getAccountId(),
                accountCore.getStatus(),
                accountCore.getOpenedAt(),
                BigDecimal.ZERO // Placeholder - would be calculated from events in reality
        );
    }
}