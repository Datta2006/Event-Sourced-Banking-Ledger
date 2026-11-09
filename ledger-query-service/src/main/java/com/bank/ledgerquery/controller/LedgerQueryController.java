package com.bank.ledgerquery.controller;

import com.bank.ledgerquery.readmodel.AccountReadModel;
import com.bank.ledgerquery.readmodel.EventView;
import com.bank.ledgerquery.readmodel.TransactionReadModel;
import com.bank.ledgerquery.service.LedgerQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Read API (docs/api_spec/ledger-query-service.yaml). The gateway strips {@code /api}.
 */
@RestController
@RequestMapping
public class LedgerQueryController {

    private final LedgerQueryService ledgerQueryService;

    public LedgerQueryController(LedgerQueryService ledgerQueryService) {
        this.ledgerQueryService = ledgerQueryService;
    }

    @GetMapping("/accounts")
    public List<AccountReadModel> listAccounts() {
        return ledgerQueryService.listAccounts();
    }

    @GetMapping("/accounts/{accountId}")
    public AccountReadModel getAccount(@PathVariable String accountId) {
        return ledgerQueryService.getAccount(accountId);
    }

    @GetMapping("/events")
    public List<EventView> listRecentEvents(
            @RequestParam(required = false) String since,
            @RequestParam(defaultValue = "50") int limit) {
        OffsetDateTime sinceInstant = since == null || since.isBlank()
                ? null : OffsetDateTime.parse(since);
        return ledgerQueryService.listRecentEvents(sinceInstant, limit);
    }

    @GetMapping("/transactions")
    public List<TransactionReadModel> listTransactions(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        OffsetDateTime fromInstant = from == null || from.isBlank() ? null : OffsetDateTime.parse(from);
        OffsetDateTime toInstant = to == null || to.isBlank() ? null : OffsetDateTime.parse(to);
        return ledgerQueryService.listTransactions(accountId, fromInstant, toInstant, page, size);
    }

    @GetMapping("/accounts/{accountId}/events")
    public List<EventView> listAccountEvents(@PathVariable String accountId) {
        return ledgerQueryService.listAccountEvents(accountId);
    }

    @ExceptionHandler(LedgerQueryService.AccountNotFoundException.class)
    public ProblemDetail notFound(LedgerQueryService.AccountNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Not Found");
        return problem;
    }
}
