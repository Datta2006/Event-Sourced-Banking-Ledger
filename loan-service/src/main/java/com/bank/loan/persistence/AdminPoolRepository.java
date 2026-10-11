package com.bank.loan.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Basic CRUD/finder access to admin_pool. */
public interface AdminPoolRepository extends JpaRepository<AdminPool, UUID> {
}
