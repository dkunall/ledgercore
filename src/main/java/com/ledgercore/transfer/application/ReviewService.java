package com.ledgercore.transfer.application;

import com.ledgercore.ledger.application.LedgerService;
import com.ledgercore.ledger.domain.EntryType;
import com.ledgercore.shared.domain.Actor;
import com.ledgercore.shared.domain.TransferId;
import com.ledgercore.shared.error.BusinessRuleException;
import com.ledgercore.shared.error.NotFoundException;
import com.ledgercore.shared.error.StateConflictException;
import com.ledgercore.transfer.domain.Transfer;
import com.ledgercore.transfer.domain.TransferStatus;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maker-checker for transfers that fraud sent to review. The funds are already on hold, so approval
 * cannot fail for lack of balance.
 */
@Service
public class ReviewService {

    private final TransferRepository transfers;
    private final TransferLifecycle lifecycle;
    private final LedgerService ledger;
    private final Clock clock;

    public ReviewService(TransferRepository transfers, TransferLifecycle lifecycle, LedgerService ledger, Clock clock) {
        this.transfers = transfers;
        this.lifecycle = lifecycle;
        this.ledger = ledger;
        this.clock = clock;
    }

    @Transactional
    public TransferView approve(Actor checker, TransferId id) {
        Transfer transfer = lockInReview(id);
        if (transfer.initiatedBy().equals(checker.subject())) {
            throw new BusinessRuleException("maker-checker", "A transfer cannot be approved by the user who made it");
        }
        Transfer approved = transfer.approvedBy(checker.subject());
        Transfer next;
        if (approved.isExternal()) {
            next = approved.moveTo(TransferStatus.HELD, clock.instant());
        } else {
            ledger.captureHold(id, approved.destinationAccount(), EntryType.TRANSFER);
            next = approved.moveTo(TransferStatus.POSTED, clock.instant());
        }
        return TransferView.of(lifecycle.save(checker, "TRANSFER_APPROVED", next));
    }

    @Transactional
    public TransferView reject(Actor checker, TransferId id, String reason) {
        Transfer transfer = lockInReview(id);
        ledger.releaseHold(id);
        Transfer next = transfer.moveTo(TransferStatus.REJECTED, reason, clock.instant());
        return TransferView.of(lifecycle.save(checker, "TRANSFER_REJECTED", next));
    }

    /**
     * Called by the expiry job; a transfer already decided by a human is left alone.
     */
    @Transactional
    public boolean expire(TransferId id) {
        Transfer transfer = transfers.lock(id).orElse(null);
        if (transfer == null || transfer.status() != TransferStatus.REVIEW) {
            return false;
        }
        ledger.releaseHold(id);
        lifecycle.save(Actor.system(), "TRANSFER_REVIEW_EXPIRED",
            transfer.moveTo(TransferStatus.REJECTED, "review-expired", clock.instant()));
        return true;
    }

    private Transfer lockInReview(TransferId id) {
        Transfer transfer = transfers.lock(id)
            .orElseThrow(() -> new NotFoundException("transfer-not-found", "Transfer " + id + " not found"));
        if (transfer.status() != TransferStatus.REVIEW) {
            throw new StateConflictException("not-in-review", "Transfer " + id + " is " + transfer.status());
        }
        return transfer;
    }
}
