package com.ledgercore.transfer.application;

import com.ledgercore.audit.application.AuditLog;
import com.ledgercore.shared.domain.Actor;
import com.ledgercore.transfer.domain.Transfer;
import com.ledgercore.transfer.domain.TransferStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Persists a status change together with everything that must accompany it: audit row,
 * outbox event and metric. Keeping this in one place means no path can forget one of them.
 */
@Component
class TransferLifecycle {

    private final TransferRepository transfers;
    private final TransferEvents events;
    private final AuditLog audit;
    private final MeterRegistry meters;

    TransferLifecycle(TransferRepository transfers, TransferEvents events, AuditLog audit, MeterRegistry meters) {
        this.transfers = transfers;
        this.events = events;
        this.audit = audit;
        this.meters = meters;
    }

    Transfer save(Actor actor, String action, Transfer next) {
        Transfer saved = transfers.update(next);
        Map<String, Object> details = new HashMap<>();
        details.put("status", saved.status().name());
        details.put("amountMinor", saved.amount().minor());
        details.put("currency", saved.amount().currencyCode());
        if (saved.failureReason() != null) {
            details.put("reason", saved.failureReason());
        }
        audit.record(actor, action, "transfer", saved.id().toString(), details);
        if (saved.status() != TransferStatus.INITIATED) {
            events.statusChanged(saved);
        }
        if (saved.isExternal() && saved.status() == TransferStatus.HELD) {
            events.railPaymentRequested(saved);
        }
        countAfterCommit(saved);
        return saved;
    }

    private void countAfterCommit(Transfer saved) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                meters.counter("ledgercore.transfers", "type", saved.type().name(), "status", saved.status().name())
                    .increment();
            }
        });
    }
}
