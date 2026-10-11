package com.bank.accountcmd.kafka;

import com.bank.accountcmd.model.DepositCommand;
import com.bank.accountcmd.model.TransferCommand;
import com.bank.accountcmd.model.WithdrawCommand;
import com.bank.accountcmd.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for processing account commands.
 * Listens to the account.commands topic and processes deposit, withdraw, and transfer commands.
 * This implements the Kafka partition-per-account ordering requirement (v2 §5.2).
 */
@Component
public class KafkaCommandConsumer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaCommandConsumer.class);

    private final AccountService accountService;

    public KafkaCommandConsumer(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Process incoming commands from the account.commands topic.
     * The @KafkaListener annotation automatically handles partitioning and ordering.
     * Messages with the same key (accountId) go to the same partition and are processed in order.
     */
    @KafkaListener(topics = "account.commands", groupId = "account-command-service")
    public void processCommand(Object command) {
        logger.info("Received command: {}", command);
        
        if (command instanceof DepositCommand) {
            accountService.processDeposit((DepositCommand) command);
        } else if (command instanceof WithdrawCommand) {
            accountService.processWithdraw((WithdrawCommand) command);
        } else if (command instanceof TransferCommand) {
            accountService.processTransfer((TransferCommand) command);
        } else {
            logger.warn("Unknown command type received: {}", command.getClass().getName());
        }
    }
}