package com.bank.loan.controller;

import com.bank.loan.model.LoanRequestCommand;
import com.bank.loan.model.LoanViews.AdminPoolView;
import com.bank.loan.model.LoanViews.AmountRequest;
import com.bank.loan.model.LoanViews.DecisionRequest;
import com.bank.loan.model.LoanViews.LoanQueueView;
import com.bank.loan.model.LoanViews.LoanRequestView;
import com.bank.loan.model.LoanViews.PoolReplenishmentView;
import com.bank.loan.model.LoanViews.RepaymentRequest;
import com.bank.loan.service.LoanService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Loan API (docs/api_spec/loan-service.yaml); gateway strips {@code /api}. */
@RestController
@RequestMapping("/loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @PostMapping
    public ResponseEntity<LoanRequestView> request(
            @RequestBody LoanRequestCommand command,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        LoanRequestView view = loanService.request(command,
                key(command.getIdempotencyKey(), idempotencyKey), correlation(request));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(view);
    }

    @GetMapping
    public List<LoanRequestView> list(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) String status) {
        return loanService.list(accountId, status);
    }

    @GetMapping("/queue")
    public LoanQueueView queue() {
        return loanService.queueView();
    }

    @GetMapping("/pool")
    public AdminPoolView pool() {
        return loanService.pool();
    }

    @PostMapping("/pool/replenish")
    public AdminPoolView replenish(
            @RequestBody AmountRequest body,
            HttpServletRequest request) {
        return loanService.replenish(body.amount(), correlation(request));
    }

    @GetMapping("/pool/history")
    public List<PoolReplenishmentView> poolHistory() {
        return loanService.poolHistory();
    }

    @GetMapping("/{loanId}")
    public LoanRequestView get(@PathVariable UUID loanId) {
        return loanService.get(loanId);
    }

    @PostMapping("/{loanId}/decision")
    public LoanRequestView decide(
            @PathVariable UUID loanId,
            @RequestBody DecisionRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        return loanService.decide(loanId, body.decision(), body.note(),
                idempotencyKey, correlation(request));
    }

    @PostMapping("/{loanId}/repayment")
    public LoanRequestView repay(
            @PathVariable UUID loanId,
            @RequestBody RepaymentRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        return loanService.repay(loanId, body.amount(), idempotencyKey, correlation(request));
    }

    private static String correlation(HttpServletRequest request) {
        return request.getHeader("X-Correlation-Id");
    }

    private static String key(String bodyKey, String headerKey) {
        if (headerKey != null && !headerKey.isBlank()) {
            return headerKey;
        }
        return bodyKey != null && !bodyKey.isBlank() ? bodyKey : null;
    }
}
