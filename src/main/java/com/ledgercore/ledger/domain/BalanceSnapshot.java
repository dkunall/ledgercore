package com.ledgercore.ledger.domain;

import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.Money;

public record BalanceSnapshot(AccountId account, Money balance, Money held, boolean allowNegative, String lastHash) {

    public Money available() {
        return balance.minus(held);
    }

    public BalanceSnapshot post(Money delta, String newHash) {
        return new BalanceSnapshot(account, balance.plus(delta), held, allowNegative, newHash);
    }

    public BalanceSnapshot hold(Money amount) {
        return new BalanceSnapshot(account, balance, held.plus(amount), allowNegative, lastHash);
    }

    public BalanceSnapshot releaseHold(Money amount) {
        return new BalanceSnapshot(account, balance, held.minus(amount), allowNegative, lastHash);
    }

    public boolean canCover(Money debit) {
        return allowNegative || available().compareTo(debit) >= 0;
    }
}
