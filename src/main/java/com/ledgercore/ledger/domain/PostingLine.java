package com.ledgercore.ledger.domain;

import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.Money;
import java.util.Objects;

/**
 * One side of an entry. Negative amounts debit the account, positive amounts credit it.
 */
public record PostingLine(AccountId account, Money amount) {

    public PostingLine {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(amount, "amount");
        if (amount.isZero()) {
            throw new IllegalArgumentException("Posting amount must not be zero");
        }
    }

    public PostingLine reversed() {
        return new PostingLine(account, amount.negate());
    }
}
