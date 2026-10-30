package com.ledgercore.transfer.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ledgercore.fraud.domain.FraudAssessment;
import com.ledgercore.transfer.domain.Transfer;
import com.ledgercore.transfer.domain.TransferStatus;
import com.ledgercore.transfer.domain.TransferType;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransferView(UUID id, TransferType type, TransferStatus status, UUID sourceAccountId,
                           UUID destinationAccountId, String destinationRef, long amountMinor, String currency,
                           String reference, String failureReason, String approvedBy, Instant createdAt,
                           Instant updatedAt, FraudAssessment fraud) {

    public static TransferView of(Transfer t) {
        return of(t, null);
    }

    public static TransferView of(Transfer t, FraudAssessment fraud) {
        return new TransferView(t.id().value(), t.type(), t.status(), t.source().value(),
            t.destinationAccount() == null ? null : t.destinationAccount().value(), t.destinationRef(),
            t.amount().minor(), t.amount().currencyCode(), t.reference(), t.failureReason(), t.approvedBy(),
            t.createdAt(), t.updatedAt(), fraud);
    }
}
