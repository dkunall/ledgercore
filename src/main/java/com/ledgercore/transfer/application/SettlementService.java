package com.ledgercore.transfer.application;

import com.ledgercore.account.domain.SystemAccounts;
import com.ledgercore.ledger.application.LedgerService;
import com.ledgercore.ledger.domain.EntryType;
import com.ledgercore.outbox.application.ProcessedEvents;
import com.ledgercore.shared.domain.Actor;
import com.ledgercore.shared.domain.TransferId;
import com.ledgercore.shared.error.NotFoundException;
import com.ledgercore.shared.error.StateConflictException;
import com.ledgercore.transfer.domain.Transfer;
import com.ledgercore.transfer.domain.TransferStatus;
import com.ledgercore.transfer.domain.TransferType;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finishes transfers after the fact: the rail's answer for external payouts, and ops reversals.
 */
@Service
public class SettlementService {

    static final String RAIL_RESULT_CONSUMER = "transfer-rail-result";

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final TransferRepository transfers;
    private final TransferLifecycle lifecycle;
    private final LedgerService ledger;
    private final ProcessedEvents processed;
    private final Clock clock;

    public SettlementService(TransferRepository transfers, TransferLifecycle lifecycle, LedgerService ledger,
                             ProcessedEvents processed, Clock clock) {
        this.transfers = transfers;
        this.lifecycle = lifecycle;
        this.ledger = ledger;
        this.processed = processed;
        this.clock = clock;
    }

    @Transactional
    public void onRailResult(UUID eventId, TransferId id, boolean accepted, String reason) {
        if (!processed.firstTime(RAIL_RESULT_CONSUMER, eventId)) {
            return;
        }
        Transfer transfer = transfers.lock(id).orElse(null);
        if (transfer == null || transfer.status() != TransferStatus.HELD) {
            log.warn("Ignoring rail result for transfer {} in state {}", id,
                transfer == null ? "missing" : transfer.status());
            return;
        }
        Transfer next;
        if (accepted) {
            ledger.captureHold(id, SystemAccounts.RAIL_CLEARING_INR, EntryType.CAPTURE);
            next = transfer.moveTo(TransferStatus.POSTED, clock.instant());
        } else {
            ledger.releaseHold(id);
            next = transfer.moveTo(TransferStatus.FAILED, reason == null ? "rail-rejected" : reason, clock.instant());
        }
        lifecycle.save(Actor.system(), accepted ? "RAIL_SETTLED" : "RAIL_REJECTED", next);
    }

    /**
     * Only internal transfers: money that left through the rail cannot be pulled back by a ledger entry.
     */
    @Transactional
    public TransferView reverse(Actor actor, TransferId id, String reason) {
        Transfer transfer = transfers.lock(id)
            .orElseThrow(() -> new NotFoundException("transfer-not-found", "Transfer " + id + " not found"));
        if (transfer.type() != TransferType.INTERNAL) {
            throw new StateConflictException("not-reversible", "Only internal transfers can be reversed");
        }
        Transfer next = transfer.moveTo(TransferStatus.REVERSED, reason, clock.instant());
        ledger.reverse(id);
        return TransferView.of(lifecycle.save(actor, "TRANSFER_REVERSED", next));
    }
}
