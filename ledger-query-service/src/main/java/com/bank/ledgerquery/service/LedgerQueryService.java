package com.bank.ledgerquery.service;

import com.bank.ledgerquery.readmodel.AccountReadModel;
import com.bank.ledgerquery.readmodel.AccountReadModelRepository;
import com.bank.ledgerquery.readmodel.TransactionReadModel;
import com.bank.ledgerquery.readmodel.TransactionReadModelRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for querying account and transaction data.
 * Implements requirements from HLD_LLD_SystemDesign_v2.md:
 * - Read model (MongoDB/FerretDB) + replay/recovery from sharded ledger
 * In a real implementation, this would project events from the ledger_events
 * store into read-optimized data structures.
 */
@Service
public class LedgerQueryService {

    @Autowired
    private AccountReadModelRepository accountReadModelRepository;

    @Autowired
    private TransactionReadModelRepository transactionReadModelRepository;

    /**
     * Get account information for display.
     * In a real implementation, this would query the read model
     * which is populated by projecting events from the ledger.
     */
    @Transactional(readOnly = true)
    public AccountReadModel getAccount(String accountId) {
        return accountReadModelRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Account not found"));
    }

    /**
     * Get transactions for an account.
     * In a real implementation, this would query the read model
     * which is populated by projecting events from the ledger.
     */
    @Transactional(readOnly = true)
    public List<TransactionReadModel> getAccountTransactions(String accountId) {
        return transactionReadModelRepository.findByAccountId(accountId);
    }

    /**
     * Process a ledger event to update the read model.
     * In a real implementation, this would be called by a background processor
     * that reads from the ledger_events table and updates the read models.
     */
    @Transactional
    public void processLedgerEvent(String eventType, String payload) {
        // In a real implementation, we would parse the event and update
        // the appropriate read models (AccountReadModel, TransactionReadModel, etc.)
        // For now, we'll just log that we're processing the event
        System.out.println("Processing ledger event for read model update: " + eventType);
        
        // Example of what a real implementation might do:
        // switch (eventType) {
        //     case "MoneyDeposited":
        //     case "MoneyWithdrawn":
        //         // Update account balance and create transaction record
        //         break;
        //     case "AccountOpened":
        //         // Create new account record
        //         break;
        //     // ... other event types
        // }
    }
}