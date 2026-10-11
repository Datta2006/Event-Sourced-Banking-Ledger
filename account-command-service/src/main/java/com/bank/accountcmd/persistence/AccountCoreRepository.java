package com.bank.accountcmd.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Basic CRUD/finder access to account_core. */
public interface AccountCoreRepository extends JpaRepository<AccountCore, UUID> {
}
