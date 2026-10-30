package com.ledgercore.transfer.application;

import com.ledgercore.shared.crypto.Sha256;
import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.Money;

public record DepositCommand(AccountId account, Money amount, String reference) {

    public String fingerprint() {
        return Sha256.hex(String.join("|", "deposit", account.toString(), Long.toString(amount.minor()),
            amount.currencyCode(), String.valueOf(reference)));
    }
}
