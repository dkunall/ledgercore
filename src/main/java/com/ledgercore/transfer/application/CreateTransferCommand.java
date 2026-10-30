package com.ledgercore.transfer.application;

import com.ledgercore.shared.crypto.Sha256;
import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.Money;
import java.util.Objects;

public record CreateTransferCommand(AccountId source, AccountId destinationAccount, String destinationRef,
                                    Money amount, String reference, String deviceId) {

    public CreateTransferCommand {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(amount, "amount");
        if ((destinationAccount == null) == (destinationRef == null)) {
            throw new IllegalArgumentException("Exactly one of destination account or reference is required");
        }
    }

    public boolean external() {
        return destinationRef != null;
    }

    /**
     * Hash of the fields that define the payment. The device id is metadata and is left out, so a retry
     * from a different device with the same key still replays.
     */
    public String fingerprint() {
        return Sha256.hex(String.join("|", "transfer", source.toString(), String.valueOf(destinationAccount),
            String.valueOf(destinationRef), Long.toString(amount.minor()), amount.currencyCode(),
            String.valueOf(reference)));
    }
}
