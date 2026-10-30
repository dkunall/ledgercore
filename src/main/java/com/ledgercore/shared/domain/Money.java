package com.ledgercore.shared.domain;

import com.ledgercore.shared.error.InvalidRequestException;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount in minor units (paise, cents) of a single currency. Floating point never touches money.
 */
public record Money(long minor, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money of(long minor, String currencyCode) {
        try {
            return new Money(minor, Currency.getInstance(currencyCode));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("unknown-currency", "Unknown currency " + currencyCode);
        }
    }

    public static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minor, other.minor), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(minor, other.minor), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(minor), currency);
    }

    public boolean isPositive() {
        return minor > 0;
    }

    public boolean isNegative() {
        return minor < 0;
    }

    public boolean isZero() {
        return minor == 0;
    }

    public String currencyCode() {
        return currency.getCurrencyCode();
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(minor, other.minor);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + currency + " vs " + other.currency);
        }
    }

    @Override
    public String toString() {
        return minor + " " + currency.getCurrencyCode();
    }
}
