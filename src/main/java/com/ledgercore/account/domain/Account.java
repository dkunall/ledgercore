package com.ledgercore.account.domain;

import com.ledgercore.shared.domain.AccountId;
import java.time.Instant;
import java.util.Currency;

public record Account(AccountId id, String ownerId, AccountType type, Currency currency,
                      AccountStatus status, Instant createdAt) {

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }
}
