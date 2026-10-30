package com.ledgercore.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.Money;
import com.ledgercore.shared.domain.TransferId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostingCommandTest {

    private final AccountId a = new AccountId(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
    private final AccountId b = new AccountId(UUID.fromString("00000000-0000-0000-0000-00000000000b"));
    private final AccountId c = new AccountId(UUID.fromString("00000000-0000-0000-0000-00000000000c"));

    @Test
    void moveProducesABalancedPair() {
        PostingCommand entry = PostingCommand.move(TransferId.random(), EntryType.TRANSFER, a, b, inr(500));

        assertThat(entry.lines()).extracting(PostingLine::amount).containsExactly(inr(-500), inr(500));
    }

    @Test
    void rejectsUnbalancedEntries() {
        List<PostingLine> lines = List.of(new PostingLine(a, inr(-500)), new PostingLine(b, inr(499)));

        assertThatThrownBy(() -> new PostingCommand(UUID.randomUUID(), TransferId.random(), EntryType.TRANSFER,
            lines, null)).hasMessageContaining("sum to zero");
    }

    @Test
    void rejectsTheSameAccountTwice() {
        List<PostingLine> lines = List.of(new PostingLine(a, inr(-5)), new PostingLine(a, inr(5)));

        assertThatThrownBy(() -> new PostingCommand(UUID.randomUUID(), TransferId.random(), EntryType.TRANSFER,
            lines, null)).hasMessageContaining("twice");
    }

    @Test
    void reversalNegatesEveryLineAndPointsAtTheOriginal() {
        UUID original = UUID.randomUUID();
        List<PostingLine> lines = List.of(new PostingLine(a, inr(-300)), new PostingLine(b, inr(300)));

        PostingCommand reversal = PostingCommand.reversalOf(original, TransferId.random(), lines);

        assertThat(reversal.reversesEntryId()).isEqualTo(original);
        assertThat(reversal.lines()).extracting(PostingLine::amount).containsExactly(inr(300), inr(-300));
    }

    @Test
    void lockOrderIsIndependentOfDirection() {
        PostingCommand forward = PostingCommand.move(TransferId.random(), EntryType.TRANSFER, c, a, inr(1));
        PostingCommand backward = PostingCommand.move(TransferId.random(), EntryType.TRANSFER, a, c, inr(1));

        assertThat(forward.accountsInLockOrder()).containsExactly(a, c);
        assertThat(backward.accountsInLockOrder()).containsExactly(a, c);
    }

    private static Money inr(long minor) {
        return Money.of(minor, "INR");
    }
}
