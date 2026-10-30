package com.ledgercore.ledger.domain;

import com.ledgercore.shared.domain.AccountId;

/**
 * Replays an account's postings in id order and checks the hash links and running balance.
 */
public final class ChainVerifier {

    private final AccountId account;
    private String expectedPrev = HashChain.GENESIS;
    private long running;
    private long checked;
    private IntegrityReport failure;

    public ChainVerifier(AccountId account) {
        this.account = account;
    }

    public void accept(StoredPosting posting) {
        if (failure != null) {
            return;
        }
        checked++;
        running = Math.addExact(running, posting.amount().minor());
        String recomputed = HashChain.next(posting.prevHash(), posting.entryId(), posting.account(), posting.amount(),
            posting.balanceAfter());
        if (!posting.prevHash().equals(expectedPrev)) {
            failure = IntegrityReport.broken(account, checked, posting.id(), "previous hash does not link");
        } else if (!recomputed.equals(posting.hash())) {
            failure = IntegrityReport.broken(account, checked, posting.id(), "hash does not match posting contents");
        } else if (running != posting.balanceAfter()) {
            failure = IntegrityReport.broken(account, checked, posting.id(), "running balance does not match");
        }
        expectedPrev = posting.hash();
    }

    public IntegrityReport finish(BalanceSnapshot current) {
        if (failure != null) {
            return failure;
        }
        if (!expectedPrev.equals(current.lastHash())) {
            return IntegrityReport.broken(account, checked, null, "balance row hash does not match last posting");
        }
        if (running != current.balance().minor()) {
            return IntegrityReport.broken(account, checked, null, "balance row does not equal sum of postings");
        }
        return IntegrityReport.ok(account, checked);
    }
}
