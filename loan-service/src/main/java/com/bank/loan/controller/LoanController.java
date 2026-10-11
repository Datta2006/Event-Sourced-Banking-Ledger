package com.bank.loan.controller;

import com.bank.loan.model.LoanRequestCommand;
import com.bank.loan.service.LoanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for loan operations.
 * Handles HTTP requests and delegates to the LoanService.
 */
@RestController
@RequestMapping("/loans")
public class LoanController {

    @Autowired
    private LoanService loanService;

    /**
     * Request a loan.
     */
    @PostMapping("/request")
    public ResponseEntity<Void> requestLoan(@RequestBody LoanRequestCommand command) {
        loanService.processLoanRequest(command);
        return ResponseEntity.accepted().build();
    }
}