package com.ledgercore.rail.application;

import com.ledgercore.outbox.application.OutboxWriter;
import com.ledgercore.outbox.application.ProcessedEvents;
import com.ledgercore.shared.messaging.Topics;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Takes payout requests off Kafka and reports the rail's answer back through the outbox, so the
 * result is published even if this process dies right after the rail call.
 */
@Service
public class RailGateway {

    static final String CONSUMER = "rail-gateway";

    private final MockRail rail;
    private final ProcessedEvents processed;
    private final OutboxWriter outbox;

    public RailGateway(MockRail rail, ProcessedEvents processed, OutboxWriter outbox) {
        this.rail = rail;
        this.processed = processed;
        this.outbox = outbox;
    }

    @Transactional
    public void handle(UUID eventId, UUID transferId, String destinationRef, long amountMinor, String currency) {
        if (!processed.firstTime(CONSUMER, eventId)) {
            return;
        }
        MockRail.RailOutcome outcome = rail.submit(transferId, destinationRef, amountMinor, currency);
        outbox.append(Topics.RAIL_RESULTS, transferId.toString(), "RailPaymentResult",
            new RailPaymentResult(transferId, outcome.accepted(), outcome.railReference(), outcome.reason()));
    }

    record RailPaymentResult(UUID transferId, boolean accepted, String railReference, String reason) {
    }
}
