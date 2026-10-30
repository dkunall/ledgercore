package com.ledgercore.transfer.domain;

import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.Money;
import com.ledgercore.shared.domain.TransferId;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable; every state change goes through {@link #moveTo}, which enforces the state machine.
 */
public record Transfer(TransferId id, TransferType type, AccountId source, AccountId destinationAccount,
                       String destinationRef, Money amount, String reference, TransferStatus status,
                       String failureReason, String initiatedBy, String approvedBy, Instant createdAt,
                       Instant updatedAt, long version) {

    public Transfer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
        if ((type == TransferType.EXTERNAL) != (destinationRef != null)
            || (type == TransferType.EXTERNAL) == (destinationAccount != null)) {
            throw new IllegalArgumentException("External transfers need a destination reference, others an account");
        }
        if (source.equals(destinationAccount)) {
            throw new IllegalArgumentException("Source and destination must differ");
        }
    }

    public static Transfer initiate(TransferType type, AccountId source, AccountId destinationAccount,
                                    String destinationRef, Money amount, String reference, String initiatedBy,
                                    Instant now) {
        return new Transfer(TransferId.random(), type, source, destinationAccount, destinationRef, amount, reference,
            TransferStatus.INITIATED, null, initiatedBy, null, now, now, 0);
    }

    public Transfer moveTo(TransferStatus next, Instant now) {
        return moveTo(next, null, now);
    }

    public Transfer moveTo(TransferStatus next, String reason, Instant now) {
        if (!status.canMoveTo(next)) {
            throw new IllegalTransitionException(status, next);
        }
        return new Transfer(id, type, source, destinationAccount, destinationRef, amount, reference, next, reason,
            initiatedBy, approvedBy, createdAt, now, version);
    }

    public Transfer approvedBy(String approver) {
        return new Transfer(id, type, source, destinationAccount, destinationRef, amount, reference, status,
            failureReason, initiatedBy, approver, createdAt, updatedAt, version);
    }

    public boolean isExternal() {
        return type == TransferType.EXTERNAL;
    }
}
