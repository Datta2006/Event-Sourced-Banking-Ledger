package com.bank.accountcmd.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Basic CRUD/finder access to the event store — no business logic in this slice. */
public interface LedgerEventRepository extends JpaRepository<LedgerEvent, UUID> {
}
