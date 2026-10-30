package com.ledgercore.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChainVerifierTest {

    private final AccountId account = new AccountId(UUID.randomUUID());

    @Test
    void acceptsAnUntouchedChain() {
        List<StoredPosting> chain = chainOf(1_000, -250, 75);

        IntegrityReport report = verify(chain, 825, chain.getLast().hash());

        assertThat(report.valid()).isTrue();
        assertThat(report.postingsChecked()).isEqualTo(3);
    }

    @Test
    void pinpointsAnEditedAmount() {
        List<StoredPosting> chain = new ArrayList<>(chainOf(1_000, -250, 75));
        StoredPosting edited = chain.get(1);
        chain.set(1, new StoredPosting(edited.id(), edited.entryId(), account, inr(-25), edited.balanceAfter(),
            edited.prevHash(), edited.hash()));

        IntegrityReport report = verify(chain, 825, chain.getLast().hash());

        assertThat(report.valid()).isFalse();
        assertThat(report.firstBrokenPostingId()).isEqualTo(2L);
    }

    @Test
    void detectsADeletedPosting() {
        List<StoredPosting> chain = new ArrayList<>(chainOf(1_000, -250, 75));
        chain.remove(1);

        IntegrityReport report = verify(chain, 825, chain.getLast().hash());

        assertThat(report.valid()).isFalse();
        assertThat(report.problem()).contains("previous hash");
    }

    @Test
    void detectsABalanceRowThatDisagreesWithPostings() {
        List<StoredPosting> chain = chainOf(1_000);

        IntegrityReport report = verify(chain, 5_000, chain.getLast().hash());

        assertThat(report.valid()).isFalse();
        assertThat(report.problem()).contains("sum of postings");
    }

    private IntegrityReport verify(List<StoredPosting> chain, long balance, String lastHash) {
        ChainVerifier verifier = new ChainVerifier(account);
        chain.forEach(verifier::accept);
        return verifier.finish(new BalanceSnapshot(account, inr(balance), inr(0), false, lastHash));
    }

    private List<StoredPosting> chainOf(long... amounts) {
        List<StoredPosting> chain = new ArrayList<>();
        String prev = HashChain.GENESIS;
        long running = 0;
        for (int i = 0; i < amounts.length; i++) {
            UUID entry = UUID.randomUUID();
            running += amounts[i];
            String hash = HashChain.next(prev, entry, account, inr(amounts[i]), running);
            chain.add(new StoredPosting(i + 1, entry, account, inr(amounts[i]), running, prev, hash));
            prev = hash;
        }
        return chain;
    }

    private static Money inr(long minor) {
        return Money.of(minor, "INR");
    }
}
