package com.ledgercore.fraud.domain;

public enum DenylistKind {
    DEVICE, ACCOUNT, DESTINATION;

    public String key(String value) {
        return name() + ":" + value;
    }
}
