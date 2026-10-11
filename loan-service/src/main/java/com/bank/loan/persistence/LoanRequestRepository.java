package com.bank.loan.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Basic CRUD/finder access to loan_requests — finders only, no business logic. */
public interface LoanRequestRepository extends JpaRepository<LoanRequest, UUID> {

    List<LoanRequest> findByAccountId(UUID accountId);

    List<LoanRequest> findByStatus(String status);
}
