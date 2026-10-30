package com.ledgercore.ledger.domain;

import com.ledgercore.shared.crypto.Sha256;
import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.Money;
import java.util.UUID;

/**
 * Per-account chain: each posting commits to the previous posting's hash, so editing or removing
 * any historical posting breaks every hash after it.
 */
public final class HashChain {

    public static final String GENESIS = "0".repeat(64);

    private HashChain() {
    }

    public static String next(String previousHash, UUID entryId, AccountId account, Money amount, long balanceAfter) {
        return Sha256.hex(String.join("|", previousHash, entryId.toString(), account.toString(),
            Long.toString(amount.minor()), amount.currencyCode(), Long.toString(balanceAfter)));
    }
}
