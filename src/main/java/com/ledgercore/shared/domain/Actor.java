package com.ledgercore.shared.domain;

import java.util.Objects;

/**
 * The authenticated caller. {@code ops} marks back-office users who may act on accounts they do not own.
 */
public record Actor(String subject, boolean ops) {

    public Actor {
        Objects.requireNonNull(subject, "subject");
    }

    public static Actor system() {
        return new Actor("system", true);
    }

    public boolean canAccess(String ownerId) {
        return ops || subject.equals(ownerId);
    }
}
