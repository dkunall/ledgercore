package com.ledgercore.fraud.domain;

import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.Money;
import com.ledgercore.shared.domain.TransferId;
import java.time.Instant;

/**
 * Destination is either an account (internal) or an external reference; deviceId is optional.
 */
public record FraudContext(TransferId transferId, AccountId source, AccountId destinationAccount,
                           String destinationRef, Money amount, String deviceId, Instant at) {

    public String destinationKey() {
        return destinationAccount != null ? destinationAccount.toString() : destinationRef;
    }
}
