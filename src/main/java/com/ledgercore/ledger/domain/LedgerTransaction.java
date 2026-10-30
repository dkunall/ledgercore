package com.ledgercore.ledger.domain;

import com.ledgercore.shared.domain.Money;
import com.ledgercore.shared.domain.TransferId;
import java.time.Instant;
import java.util.UUID;

public record LedgerTransaction(long postingId, UUID entryId, TransferId transferId, EntryType entryType,
                                Money amount, Money balanceAfter, Instant createdAt) {
}
