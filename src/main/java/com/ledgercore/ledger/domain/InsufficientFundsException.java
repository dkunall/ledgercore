package com.ledgercore.ledger.domain;

import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.error.BusinessRuleException;

public class InsufficientFundsException extends BusinessRuleException {

    public InsufficientFundsException(AccountId account) {
        super("insufficient-funds", "Account " + account + " has insufficient available balance");
    }
}
