package com.bank.accountcmd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Account Command Service (v2).
 *
 * <p>Owns the write side of account aggregates: open/close/KYC, deposits,
 * withdrawals, transfers and reservation holds. Commands are validated against the
 * event-sourced aggregate, appended to the account's regional shard with a
 * per-aggregate hash chain, and published to {@code account.events}. Account writes
 * are serialised by a Redisson {@code RFairLock} keyed by account id.</p>
 */
@SpringBootApplication
@EnableScheduling
public class AccountCommandApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountCommandApplication.class, args);
    }

    /**
     * Dedicated pool for transfer sagas. Keeping sagas off the request threads
     * means a slow remote leg can never pin a Tomcat worker, and the persisted
     * saga state is resumed by {@code AccountService#recoverSagas} on restart.
     */
    @Bean("sagaExecutor")
    public ThreadPoolTaskExecutor sagaExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("transfer-saga-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
