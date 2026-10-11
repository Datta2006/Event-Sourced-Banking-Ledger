package com.bank.ledgerquery.controller;

import com.bank.ledgerquery.readmodel.AccountReadModel;
import com.bank.ledgerquery.readmodel.TransactionReadModel;
import com.bank.ledgerquery.service.LedgerQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for ledger query operations.
 * Handles HTTP requests for account and transaction data.
 */
@RestController
@RequestMapping("/ledger")
public class LedgerQueryController {

    @Autowired
    private LedgerQueryService ledgerQueryService;

    /**
     * Get account information.
     */
    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<AccountReadModel> getAccount(@PathVariable String accountId) {
        AccountReadModel account = ledgerQueryService.getAccount(accountId);
        return ResponseEntity.ok(account);
    }

    /**
     * Get transactions for an account.
     */
    @GetMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<List<TransactionReadModel>> getAccountTransactions(
            @PathVariable String accountId) {
        List<TransactionReadModel> transactions = ledgerQueryService.getAccountTransactions(accountId);
        return ResponseEntity.ok(transactions);
    }
}