package com.bank.ledgerquery.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Rebuilds the read model from the region-sharded event store when it is empty.
 * Lets a fresh FerretDB container come up with the same balances as the write side
 * without waiting for Kafka history to be replayed.
 */
@Component
public class ReadModelBootstrap {

    private static final Logger log = LoggerFactory.getLogger(ReadModelBootstrap.class);

    private final LedgerQueryService queryService;
    private final boolean enabled;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ReadModelBootstrap(LedgerQueryService queryService,
                              @Value("${app.replay.on-startup-if-empty:true}") boolean enabled) {
        this.queryService = queryService;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildIfEmpty() {
        if (!enabled || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            long accounts = queryService.accountCount();
            if (accounts == 0) {
                int count = queryService.replayAll(false);
                log.info("Read model was empty; replayed {} events from the sharded ledger", count);
            } else {
                log.info("Read model already has {} accounts; skipping startup replay", accounts);
            }
        } catch (Exception e) {
            log.error("Startup replay failed: {}", e.getMessage(), e);
        } finally {
            running.set(false);
        }
    }
}
