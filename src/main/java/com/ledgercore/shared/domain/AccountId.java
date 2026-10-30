package com.ledgercore.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record AccountId(UUID value) implements Comparable<AccountId> {

    public AccountId {
        Objects.requireNonNull(value, "value");
    }

    public static AccountId random() {
        return new AccountId(UUID.randomUUID());
    }

    public static AccountId parse(String raw) {
        return new AccountId(UUID.fromString(raw));
    }

    @Override
    public int compareTo(AccountId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
