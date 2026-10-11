package com.bank.loan.service;

import com.bank.loan.model.LoanRequestCommand;
import com.bank.loan.persistence.AdminPool;
import com.bank.loan.persistence.AdminPoolRepository;
import com.bank.loan.persistence.LoanRequest;
import com.bank.loan.persistence.LoanRequestRepository;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Service handling loan operations with priority queue and atomic pool debit.
 * Implements requirements from HLD_LLD_SystemDesign_v2.md §3:
 * - Redisson RScoredSortedSet for priority queue (loan:queue)
 * - Atomic Lua script for admin pool debit
 * - Trust score-based prioritization
 */
@Service
public class LoanService {

    @Autowired
    private LoanRequestRepository loanRequestRepository;

    @Autowired
    private AdminPoolRepository adminPoolRepository;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private static final String LOAN_QUEUE_KEY = "loan:queue";
    private static final String ADMIN_POOL_BALANCE_KEY = "admin_pool:balance";
    private static final String ADMIN_POOL_ID = "admin-pool-uuid"; // In reality, this would be a UUID from DB

    /**
     * Process a loan request command.
     * Adds the request to the priority queue and triggers processing.
     */
    @Transactional
    public void processLoanRequest(LoanRequestCommand command) {
        // In a real implementation, we would check idempotency here
        // For now, we'll proceed with creating the loan request

        // Create loan request record
        LoanRequest loanRequest = new LoanRequest();
        loanRequest.setLoanId(UUID.randomUUID());
        loanRequest.setAccountId(command.getAccountId());
        loanRequest.setAmount(command.getAmount());
        // TODO: Get trust tier from Trust Score Service cache or fetch it
        // For now, we'll use a placeholder trust tier of 2 (High)
        loanRequest.setTrustTier((short) 2);
        loanRequest.setRequestedAt(OffsetDateTime.now());
        loanRequest.setStatus("QUEUED");
        loanRequestRepository.save(loanRequest);

        // Add to priority queue in Valkey/Redisson
        // Score = (3 - trustTier) * 10_000_000_000L + requestedAtEpochMillis
        // Lower score = higher priority
        long requestedAtEpochMillis = loanRequest.getRequestedAt().toInstant().toEpochMilli();
        double score = (3 - loanRequest.getTrustTier()) * 10_000_000_000L + requestedAtEpochMillis;

        RScoredSortedSet<String> loanQueue = redissonClient.getScoredSortedSet(LOAN_QUEUE_KEY);
        loanQueue.add(score, loanRequest.getLoanId().toString());

        // Trigger processing of the loan queue
        processLoanQueue();
    }

    /**
     * Process the loan queue by checking the highest priority loan
     * and attempting to debit the admin pool via atomic Lua script.
     */
    @Transactional
    public void processLoanQueue() {
        // Get the highest priority loan (lowest score)
        RScoredSortedSet<String> loanQueue = redissonClient.getScoredSortedSet(LOAN_QUEUE_KEY);
        String loanIdStr = loanQueue.pollFirst(); // Returns and removes the highest priority element

        if (loanIdStr == null) {
            // Queue is empty
            return;
        }

        UUID loanId = UUID.fromString(loanIdStr);
        LoanRequest loanLoanRequest = loanRequestRepository.findById(loanId)
                .orElseThrow(() -> new IllegalStateException("Loan request not found: " + loanId));

        // Update status to WAITING_FOR_FUNDS while we process
        loanLoanRequest.setStatus("WAITING_FOR_FUNDS");
        loanRequestRepository.save(loanLoanRequest);

        // Run atomic Lua script to debit admin pool
        // Lua script: 
        //   KEYS[1] = admin_pool:balance, ARGV[1] = requested amount
        //   if bal >= amount then
        //     DECRBY KEYS[1] amount
        //     return 1   -- approved
        //   else
        //     return 0   -- insufficient funds
        Long result = redissonClient.getScript()
                .eval(
                        RedissonClient.Mode.DEFAULT,
                        "local bal = tonumber(redis.call('GET', KEYS[1]))" +
                                "if bal >= tonumber(ARGV[1]) then" +
                                "  redis.call('DECRBY', KEYS[1], ARGV[1])" +
                                "  return 1" +
                                "else" +
                                "  return 0" +
                                "end",
                        org.redisson.client.schema.ScriptReturnType.INTEGER,
                        ADMIN_POOL_BALANCE_KEY,
                        loanLoanRequest.getAmount().toString());

        boolean approved = (result != null && result == 1);

        if (approved) {
            // Loan approved - update request status and publish events
            loanLoanRequest.setStatus("APPROVED");
            loanRequestRepository.save(loanLoanRequest);

            // Publish LoanApproved and LoanDisbursed events to Kafka
            String loanApprovedEvent = String.format(
                    "{\"loanId\":\"%s\",\"accountId\":\"%s\",\"amount\":\"%s\",\"trustTier\":%d}",
                    loanLoanRequest.getLoanId(),
                    loanLoanRequest.getAccountId(),
                    loanLoanRequest.getAmount(),
                    loanLoanRequest.getTrustTier());
            kafkaTemplate.send("loan.events", loanLoanRequest.getAccountId().toString(), loanApprovedEvent);

            loanLoanRequest.setStatus("DISBURSED");
            loanRequestRepository.save(loanLoanRequest);

            String loanDisbursedEvent = String.format(
                    "{\"loanId\":\"%s\",\"accountId\":\"%s\",\"amount\":\"%s\",\"disbursedAt\":\"%s\"}",
                    loanLoanRequest.getLoanId(),
                    loanLoanRequest.getAccountId(),
                    loanLoanRequest.getAmount(),
                    OffsetDateTime.now());
            kafkaTemplate.send("loan.events", loanLoanRequest.getAccountId().toString(), loanDisbursedEvent);

            // Update admin pool in PostgreSQL (mirror of Valkey)
            AdminPool adminPool = adminPoolRepository.findById(UUID.fromString(ADMIN_POOL_ID))
                    .orElseGet(() -> {
                        AdminPool newPool = new AdminPool();
                        newPool.setPoolId(UUID.fromString(ADMIN_POOL_ID));
                        newPool.setAvailableBalance(BigDecimal.valueOf(2000)); // Initial pool of ₹2,000
                        newPool.setVersion(0);
                        return adminPoolRepository.save(newPool);
                    });
            adminPool.setAvailableBalance(adminPool.getAvailableBalance().subtract(loanLoanRequest.getAmount()));
            adminPool.setVersion(adminPool.getVersion() + 1);
            adminPoolRepository.save(adminPool);
        } else {
            // Insufficient funds - keep in WAITING_FOR_FUNDS status
            // It will be reprocessed when the pool is replenished
            loanLoanRequest.setStatus("WAITING_FOR_FUNDS");
            loanRequestRepository.save(loanLoanRequest);

            // Put it back in the queue for retry later
            double score = (3 - loanLoanRequest.getTrustTier()) * 10_000_000_000L + 
                    loanLoanRequest.getRequestedAt().toInstant().toEpochMilli();
            loanQueue.add(score, loanIdStr);
        }
    }

    /**
     * Initialize the admin pool if it doesn't exist.
     * Called on startup to ensure the pool has funds.
     */
    @Transactional
    public void initializeAdminPool() {
        AdminPool adminPool = adminPoolRepository.findById(UUID.fromString(ADMIN_POOL_ID))
                .orElseGet(() -> {
                    AdminPool newPool = new AdminPool();
                    newPool.setPoolId(UUID.fromString(ADMIN_POOL_ID));
                    newPool.setAvailableBalance(BigDecimal.valueOf(2000)); // Initial pool of ₹2,000
                    newPool.setVersion(0);
                    return adminPoolRepository.save(newPool);
                });

        // Also initialize the Valkey mirror
        redissonClient.getBucket(ADMIN_POOL_BALANCE_KEY).set(adminPool.getAvailableBalance());
    }

    /**
     * Scheduled task to process the loan queue every 5 seconds.
     * In a production system, this might be event-driven instead of polling.
     */
    @Scheduled(fixedDelay = 5000)
    public void scheduledLoanQueueProcessing() {
        processLoanQueue();
    }
}