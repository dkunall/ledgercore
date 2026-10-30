package com.ledgercore.ledger.domain;

import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.Money;
import com.ledgercore.shared.domain.TransferId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

/**
 * A balanced journal entry waiting to be posted. Validated here so a bad entry never reaches SQL;
 * the database trigger checks the same invariant again at commit.
 */
public record PostingCommand(UUID entryId, TransferId transferId, EntryType type, List<PostingLine> lines,
                             UUID reversesEntryId) {

    public PostingCommand {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(type, "type");
        lines = List.copyOf(lines);
        if (lines.size() < 2) {
            throw new IllegalArgumentException("An entry needs at least two postings");
        }
        Money total = Money.zero(lines.getFirst().amount().currency());
        Set<AccountId> seen = new HashSet<>();
        for (PostingLine line : lines) {
            total = total.plus(line.amount());
            if (!seen.add(line.account())) {
                throw new IllegalArgumentException("Account " + line.account() + " appears twice in one entry");
            }
        }
        if (!total.isZero()) {
            throw new IllegalArgumentException("Postings must sum to zero, got " + total);
        }
        if ((type == EntryType.REVERSAL) != (reversesEntryId != null)) {
            throw new IllegalArgumentException("Only reversal entries reference another entry");
        }
    }

    public static PostingCommand move(TransferId transferId, EntryType type, AccountId from, AccountId to,
                                      Money amount) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        return new PostingCommand(UUID.randomUUID(), transferId, type,
            List.of(new PostingLine(from, amount.negate()), new PostingLine(to, amount)), null);
    }

    public static PostingCommand reversalOf(UUID originalEntryId, TransferId transferId, List<PostingLine> original) {
        return new PostingCommand(UUID.randomUUID(), transferId, EntryType.REVERSAL,
            original.stream().map(PostingLine::reversed).toList(), originalEntryId);
    }

    /**
     * Sorted so every transaction locks balance rows in the same order, which rules out deadlocks
     * between transfers that touch the same accounts in opposite directions.
     */
    public SortedSet<AccountId> accountsInLockOrder() {
        SortedSet<AccountId> ids = new TreeSet<>();
        lines.forEach(line -> ids.add(line.account()));
        return ids;
    }
}
