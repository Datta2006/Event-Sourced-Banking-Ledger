package com.bank.ledgerquery.readmodel;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Basic CRUD/finder access to the accounts read model — projection logic comes later. */
public interface AccountReadModelRepository extends MongoRepository<AccountReadModel, String> {
}
