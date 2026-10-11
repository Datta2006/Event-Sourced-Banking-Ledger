package com.bank.ledgerquery.readmodel;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/** Basic CRUD/finder access to the transactions read model — projection logic comes later. */
public interface TransactionReadModelRepository extends MongoRepository<TransactionReadModel, String> {

    List<TransactionReadModel> findByAccountId(String accountId);
}
