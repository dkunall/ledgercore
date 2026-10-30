package com.ledgercore.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.Money;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BalanceSnapshotTest {

    private final AccountId account = new AccountId(UUID.randomUUID());

    @Test
    void heldFundsAreNotAvailable() {
        BalanceSnapshot snapshot = snapshot(1_000, false).hold(inr(700));

        assertThat(snapshot.available()).isEqualTo(inr(300));
        assertThat(snapshot.canCover(inr(300))).isTrue();
        assertThat(snapshot.canCover(inr(301))).isFalse();
    }

    @Test
    void releasingAHoldRestoresAvailability() {
        BalanceSnapshot snapshot = snapshot(1_000, false).hold(inr(700)).releaseHold(inr(700));

        assertThat(snapshot.available()).isEqualTo(inr(1_000));
    }

    @Test
    void systemAccountsMayGoNegative() {
        assertThat(snapshot(0, true).canCover(inr(1_000_000))).isTrue();
    }

    private BalanceSnapshot snapshot(long balance, boolean allowNegative) {
        return new BalanceSnapshot(account, inr(balance), inr(0), allowNegative, HashChain.GENESIS);
    }

    private static Money inr(long minor) {
        return Money.of(minor, "INR");
    }
}
