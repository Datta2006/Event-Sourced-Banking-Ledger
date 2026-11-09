package com.bank.loan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Loan Service (v2 §3).
 *
 * <p>Requests enter a Valkey {@code loan:queue} scored set (tier first, FCFS
 * within tier); a worker pops the head under an atomic Lua debit of
 * {@code admin_pool:balance}. Success disburses through the account service and
 * emits {@code LoanApproved} → {@code LoanDisbursed}; failure parks the loan in
 * {@code WAITING_FOR_FUNDS} until a replenishment.</p>
 */
@SpringBootApplication
@EnableScheduling
public class LoanServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoanServiceApplication.class, args);
    }

    @Bean("loanWorker")
    public ThreadPoolTaskExecutor loanWorker() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("loan-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
