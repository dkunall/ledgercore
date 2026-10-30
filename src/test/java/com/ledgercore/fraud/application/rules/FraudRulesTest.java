package com.ledgercore.fraud.application.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgercore.fraud.application.DenylistIndex;
import com.ledgercore.fraud.application.DenylistRepository;
import com.ledgercore.fraud.application.FraudProperties;
import com.ledgercore.fraud.application.VelocityUnavailableException;
import com.ledgercore.fraud.domain.DenylistKind;
import com.ledgercore.fraud.domain.FraudContext;
import com.ledgercore.fraud.domain.ScoreCard;
import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.Money;
import com.ledgercore.shared.domain.TransferId;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FraudRulesTest {

    private final FraudProperties properties = new FraudProperties(50, 100, Duration.ofMinutes(1), 20, 50,
        1_000_000, 100_000, 1_000, 0.001);

    @Test
    void velocityEscalatesFromReviewToBlock() {
        assertThat(velocityScore(20)).isZero();
        assertThat(velocityScore(21)).isEqualTo(50);
        assertThat(velocityScore(51)).isEqualTo(100);
    }

    @Test
    void velocityOutageSendsOnlyLargeAmountsToReview() {
        VelocityRule rule = new VelocityRule((key, member, at, window) -> {
            throw new VelocityUnavailableException("redis down", new RuntimeException());
        }, properties);

        ScoreCard small = new ScoreCard();
        rule.evaluate(context(99_999, null), small);
        ScoreCard large = new ScoreCard();
        rule.evaluate(context(100_000, null), large);

        assertThat(small.score()).isZero();
        assertThat(small.degraded()).isTrue();
        assertThat(large.score()).isEqualTo(50);
        assertThat(large.reasons()).contains("VELOCITY_UNAVAILABLE", "DEGRADED_REVIEW");
    }

    @Test
    void highValueStartsAtTheThreshold() {
        HighValueRule rule = new HighValueRule(properties);
        ScoreCard below = new ScoreCard();
        rule.evaluate(context(999_999, null), below);
        ScoreCard at = new ScoreCard();
        rule.evaluate(context(1_000_000, null), at);

        assertThat(below.score()).isZero();
        assertThat(at.score()).isEqualTo(50);
    }

    @Test
    void denylistedDeviceBlocksOnItsOwn() {
        FakeDenylist repository = new FakeDenylist();
        repository.add(DenylistKind.DEVICE, "dev-42", "chargeback ring", "ops");
        DenylistRule rule = new DenylistRule(new DenylistIndex(repository, properties));

        ScoreCard card = new ScoreCard();
        rule.evaluate(context(100, "dev-42"), card);

        assertThat(card.score()).isEqualTo(100);
        assertThat(card.reasons()).containsExactly("DENYLISTED_DEVICE");
    }

    @Test
    void bloomFilterMissSkipsTheDatabase() {
        FakeDenylist repository = new FakeDenylist();
        DenylistRule rule = new DenylistRule(new DenylistIndex(repository, properties));

        rule.evaluate(context(100, "dev-clean"), new ScoreCard());

        assertThat(repository.containsCalls).isZero();
    }

    private int velocityScore(long count) {
        ScoreCard card = new ScoreCard();
        new VelocityRule((key, member, at, window) -> count, properties).evaluate(context(100, null), card);
        return card.score();
    }

    private static FraudContext context(long amountMinor, String deviceId) {
        return new FraudContext(TransferId.random(), new AccountId(UUID.randomUUID()), new AccountId(UUID.randomUUID()),
            null, Money.of(amountMinor, "INR"), deviceId, Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static final class FakeDenylist implements DenylistRepository {

        private final Set<String> keys = new HashSet<>();
        private int containsCalls;

        @Override
        public boolean add(DenylistKind kind, String value, String reason, String createdBy) {
            return keys.add(kind.key(value));
        }

        @Override
        public boolean contains(DenylistKind kind, String value) {
            containsCalls++;
            return keys.contains(kind.key(value));
        }

        @Override
        public List<String> allKeys() {
            return List.copyOf(keys);
        }
    }
}
