package com.ledgercore.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record TransferId(UUID value) {

    public TransferId {
        Objects.requireNonNull(value, "value");
    }

    public static TransferId random() {
        return new TransferId(UUID.randomUUID());
    }

    public static TransferId parse(String raw) {
        return new TransferId(UUID.fromString(raw));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
