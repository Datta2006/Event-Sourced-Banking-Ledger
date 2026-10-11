package com.bank.accountcmd.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Basic CRUD/finder access to account_pii. */
public interface AccountPiiRepository extends JpaRepository<AccountPii, UUID> {
}
