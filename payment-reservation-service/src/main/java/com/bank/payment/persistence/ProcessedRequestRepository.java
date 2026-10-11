package com.bank.payment.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Basic CRUD/finder access to processed_requests. */
public interface ProcessedRequestRepository extends JpaRepository<ProcessedRequest, String> {
}
