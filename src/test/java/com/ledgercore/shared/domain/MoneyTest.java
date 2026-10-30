package com.ledgercore.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgercore.shared.error.InvalidRequestException;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void addsAndSubtractsInMinorUnits() {
        Money a = Money.of(1_050, "INR");
        Money b = Money.of(25, "INR");

        assertThat(a.plus(b)).isEqualTo(Money.of(1_075, "INR"));
        assertThat(a.minus(b)).isEqualTo(Money.of(1_025, "INR"));
        assertThat(b.negate().isNegative()).isTrue();
    }

    @Test
    void refusesToMixCurrencies() {
        assertThatThrownBy(() -> Money.of(1, "INR").plus(Money.of(1, "USD")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectsOverflowInsteadOfWrapping() {
        assertThatThrownBy(() -> Money.of(Long.MAX_VALUE, "INR").plus(Money.of(1, "INR")))
            .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void unknownCurrencyIsABadRequest() {
        assertThatThrownBy(() -> Money.of(1, "XXQ")).isInstanceOf(InvalidRequestException.class);
    }
}
