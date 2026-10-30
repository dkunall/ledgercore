package com.ledgercore.account.application;

import com.ledgercore.account.domain.Account;
import com.ledgercore.shared.domain.AccountId;
import java.util.List;
import java.util.Optional;

public interface AccountRepository {

    void insert(Account account);

    Optional<Account> findById(AccountId id);

    List<Account> findByOwner(String ownerId);
}
