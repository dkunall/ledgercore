package com.ledgercore.transfer.application;

import com.ledgercore.account.application.AccountService;
import com.ledgercore.account.domain.Account;
import com.ledgercore.account.domain.SystemAccounts;
import com.ledgercore.fraud.application.FraudService;
import com.ledgercore.fraud.domain.FraudAssessment;
import com.ledgercore.fraud.domain.FraudContext;
import com.ledgercore.idempotency.application.IdempotencyService;
import com.ledgercore.ledger.application.LedgerService;
import com.ledgercore.ledger.domain.EntryType;
import com.ledgercore.ledger.domain.InsufficientFundsException;
import com.ledgercore.ledger.domain.PostingCommand;
import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.Actor;
import com.ledgercore.shared.domain.TransferId;
import com.ledgercore.shared.error.BusinessRuleException;
import com.ledgercore.shared.error.NotFoundException;
import com.ledgercore.transfer.domain.Transfer;
import com.ledgercore.transfer.domain.TransferStatus;
import com.ledgercore.transfer.domain.TransferType;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates transfers. An internal transfer that passes fraud checks is posted in the same database
 * transaction that records it; only the external leg goes through the outbox and the rail.
 */
@Service
public class TransferService {

    private static final int CREATED = 201;

    private final TransferRepository transfers;
    private final TransferLifecycle lifecycle;
    private final AccountService accounts;
    private final LedgerService ledger;
    private final FraudService fraud;
    private final IdempotencyService idempotency;
    private final Clock clock;

    public TransferService(TransferRepository transfers, TransferLifecycle lifecycle, AccountService accounts,
                           LedgerService ledger, FraudService fraud, IdempotencyService idempotency, Clock clock) {
        this.transfers = transfers;
        this.lifecycle = lifecycle;
        this.accounts = accounts;
        this.ledger = ledger;
        this.fraud = fraud;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    @Transactional
    public TransferResult create(Actor actor, String idempotencyKey, CreateTransferCommand command) {
        Optional<TransferView> replay = idempotency.begin(actor.subject(), idempotencyKey, command.fingerprint(),
            TransferView.class);
        if (replay.isPresent()) {
            return new TransferResult(replay.get(), true);
        }

        Account source = accounts.getActive(command.source());
        if (!actor.canAccess(source.ownerId())) {
            throw new NotFoundException("account-not-found", "Account " + command.source() + " not found");
        }
        requireCurrency(source, command);
        if (!command.external()) {
            requireCurrency(accounts.getActive(command.destinationAccount()), command);
        }

        TransferType type = command.external() ? TransferType.EXTERNAL : TransferType.INTERNAL;
        Transfer transfer = Transfer.initiate(type, command.source(), command.destinationAccount(),
            command.destinationRef(), command.amount(), command.reference(), actor.subject(), clock.instant());
        transfers.insert(transfer);

        FraudAssessment assessment = fraud.assess(new FraudContext(transfer.id(), transfer.source(),
            transfer.destinationAccount(), transfer.destinationRef(), transfer.amount(), command.deviceId(),
            clock.instant()));
        Transfer saved = lifecycle.save(actor, "TRANSFER_CREATED", route(transfer, assessment));

        TransferView view = TransferView.of(saved);
        idempotency.complete(actor.subject(), idempotencyKey, CREATED, view);
        return new TransferResult(view, false);
    }

    @Transactional
    public TransferResult deposit(Actor actor, String idempotencyKey, DepositCommand command) {
        Optional<TransferView> replay = idempotency.begin(actor.subject(), idempotencyKey, command.fingerprint(),
            TransferView.class);
        if (replay.isPresent()) {
            return new TransferResult(replay.get(), true);
        }
        Account account = accounts.getActive(command.account());
        if (!account.currency().equals(command.amount().currency())) {
            throw new BusinessRuleException("currency-mismatch", "Deposit currency does not match the account");
        }
        Transfer transfer = Transfer.initiate(TransferType.DEPOSIT, SystemAccounts.CASH_CLEARING_INR,
            command.account(), null, command.amount(), command.reference(), actor.subject(), clock.instant());
        transfers.insert(transfer);
        ledger.post(PostingCommand.move(transfer.id(), EntryType.DEPOSIT, transfer.source(),
            transfer.destinationAccount(), transfer.amount()));
        Transfer saved = lifecycle.save(actor, "DEPOSIT_POSTED", transfer.moveTo(TransferStatus.POSTED, clock.instant()));

        TransferView view = TransferView.of(saved);
        idempotency.complete(actor.subject(), idempotencyKey, CREATED, view);
        return new TransferResult(view, false);
    }

    /**
     * Owners of either side can read a transfer; only ops see the fraud assessment.
     */
    @Transactional(readOnly = true)
    public TransferView get(Actor actor, TransferId id) {
        Transfer transfer = transfers.find(id)
            .filter(t -> canSee(actor, t))
            .orElseThrow(() -> new NotFoundException("transfer-not-found", "Transfer " + id + " not found"));
        return TransferView.of(transfer, actor.ops() ? fraud.decisionFor(id).orElse(null) : null);
    }

    private Transfer route(Transfer transfer, FraudAssessment assessment) {
        return switch (assessment.decision()) {
            case BLOCK -> transfer.moveTo(TransferStatus.REJECTED, "fraud-blocked", clock.instant());
            case REVIEW -> holdOrFail(transfer, TransferStatus.REVIEW);
            case ALLOW -> transfer.isExternal() ? holdOrFail(transfer, TransferStatus.HELD) : postOrFail(transfer);
        };
    }

    private Transfer postOrFail(Transfer transfer) {
        try {
            ledger.post(PostingCommand.move(transfer.id(), EntryType.TRANSFER, transfer.source(),
                transfer.destinationAccount(), transfer.amount()));
            return transfer.moveTo(TransferStatus.POSTED, clock.instant());
        } catch (InsufficientFundsException e) {
            return transfer.moveTo(TransferStatus.FAILED, e.code(), clock.instant());
        }
    }

    private Transfer holdOrFail(Transfer transfer, TransferStatus next) {
        try {
            ledger.placeHold(transfer.id(), transfer.source(), transfer.amount());
            return transfer.moveTo(next, clock.instant());
        } catch (InsufficientFundsException e) {
            return transfer.moveTo(TransferStatus.FAILED, e.code(), clock.instant());
        }
    }

    private boolean canSee(Actor actor, Transfer transfer) {
        return actor.ops()
            || owns(actor, transfer.source())
            || (transfer.destinationAccount() != null && owns(actor, transfer.destinationAccount()));
    }

    private boolean owns(Actor actor, AccountId account) {
        return accounts.ownerOf(account).map(actor.subject()::equals).orElse(false);
    }

    private static void requireCurrency(Account account, CreateTransferCommand command) {
        if (!account.currency().equals(command.amount().currency())) {
            throw new BusinessRuleException("currency-mismatch",
                "Account " + account.id() + " is in " + account.currency() + ", transfer is in "
                    + command.amount().currencyCode());
        }
    }
}
