package com.ledgercore.ledger.domain;

import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.Money;
import java.util.UUID;

public record StoredPosting(long id, UUID entryId, AccountId account, Money amount, long balanceAfter,
                            String prevHash, String hash) {
}
