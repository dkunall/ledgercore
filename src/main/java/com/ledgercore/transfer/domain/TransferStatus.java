package com.ledgercore.transfer.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum TransferStatus {
    INITIATED, REVIEW, HELD, POSTED, REJECTED, FAILED, REVERSED;

    private static final Map<TransferStatus, Set<TransferStatus>> ALLOWED = new EnumMap<>(TransferStatus.class);

    static {
        ALLOWED.put(INITIATED, EnumSet.of(REVIEW, HELD, POSTED, REJECTED, FAILED));
        ALLOWED.put(REVIEW, EnumSet.of(HELD, POSTED, REJECTED));
        ALLOWED.put(HELD, EnumSet.of(POSTED, FAILED));
        ALLOWED.put(POSTED, EnumSet.of(REVERSED));
        ALLOWED.put(REJECTED, EnumSet.noneOf(TransferStatus.class));
        ALLOWED.put(FAILED, EnumSet.noneOf(TransferStatus.class));
        ALLOWED.put(REVERSED, EnumSet.noneOf(TransferStatus.class));
    }

    public boolean canMoveTo(TransferStatus next) {
        return ALLOWED.get(this).contains(next);
    }

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }
}
