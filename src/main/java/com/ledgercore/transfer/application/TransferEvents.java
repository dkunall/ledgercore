package com.ledgercore.transfer.application;

import com.ledgercore.outbox.application.OutboxWriter;
import com.ledgercore.shared.messaging.Topics;
import com.ledgercore.transfer.domain.Transfer;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class TransferEvents {

    private final OutboxWriter outbox;

    TransferEvents(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    void statusChanged(Transfer t) {
        outbox.append(Topics.TRANSFERS, t.id().toString(), "TransferStatusChanged", new TransferStatusChanged(
            t.id().value(), t.type().name(), t.status().name(), t.source().value(),
            t.destinationAccount() == null ? null : t.destinationAccount().value(),
            t.amount().minor(), t.amount().currencyCode(), t.failureReason()));
    }

    void railPaymentRequested(Transfer t) {
        outbox.append(Topics.RAIL_REQUESTS, t.id().toString(), "RailPaymentRequested",
            new RailPaymentRequested(t.id().value(), t.destinationRef(), t.amount().minor(), t.amount().currencyCode()));
    }

    record TransferStatusChanged(UUID transferId, String type, String status, UUID sourceAccountId,
                                 UUID destinationAccountId, long amountMinor, String currency, String failureReason) {
    }

    record RailPaymentRequested(UUID transferId, String destinationRef, long amountMinor, String currency) {
    }
}
