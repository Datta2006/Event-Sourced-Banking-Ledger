package com.bank.payment.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Access to the payment schema's account balance projection. */
public interface AccountBalanceRepository extends JpaRepository<AccountBalance, String> {

    @Query("select b from AccountBalance b where b.status = 'ACTIVE' order by b.accountId")
    List<AccountBalance> findAllActive();
}
