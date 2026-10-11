package com.bank.accountcmd.controller;

import com.bank.accountcmd.model.AccountResponse;
import com.bank.accountcmd.model.DepositCommand;
import com.bank.accountcmd.model.TransferCommand;
import com.bank.accountcmd.model.WithdrawCommand;
import com.bank.accountcmd.service.AccountService;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for account operations.
 * Handles HTTP requests and publishes commands to Kafka for processing.
 * This implements the Kafka partition-per-account ordering requirement (v2 §5.2).
 */
@RestController
@RequestMapping("/accounts")
public class AccountController {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private AccountService accountService;

    /**
     * Deposit money into an account.
     * Publishes a DepositCommand to the account.commands topic.
     */
    @PostMapping("/{accountId}/deposit")
    public ResponseEntity<Void> deposit(
            @PathVariable UUID accountId,
            @RequestBody DepositCommand command) {
        
        command.setAccountId(accountId);
        // Keyed by accountId for partition-per-account ordering
        kafkaTemplate.send(new ProducerRecord<>("account.commands", accountId.toString(), command));
        return ResponseEntity.accepted().build();
    }

    /**
     * Withdraw money from an account.
     * Publishes a WithdrawCommand to the account.commands topic.
     */
    @PostMapping("/{accountId}/withdraw")
    public ResponseEntity<Void> withdraw(
            @PathVariable UUID accountId,
            @RequestBody WithdrawCommand command) {
        
        command.setAccountId(accountId);
        // Keyed by accountId for partition-per-account ordering
        kafkaTemplate.send(new ProducerRecord<>("account.commands", accountId.toString(), command));
        return ResponseEntity.accepted().build();
    }

    /**
     * Transfer money between accounts.
     * Publishes a TransferCommand to the account.commands topic.
     * Note: For transfers, we key by the fromAccountId to ensure both legs
     * of the transfer are processed by the same consumer instance.
     */
    @PostMapping("/transfer")
    public ResponseEntity<Void> transfer(@RequestBody TransferCommand command) {
        // Keyed by fromAccountId for partition-per-account ordering
        kafkaTemplate.send(new ProducerRecord<>("account.commands", command.getFromAccountId().toString(), command));
        return ResponseEntity.accepted().build();
    }

    /**
     * Get account information.
     */
    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccountInfo(@PathVariable UUID accountId) {
        AccountResponse response = accountService.getAccountInfo(accountId);
        return ResponseEntity.ok(response);
    }
}