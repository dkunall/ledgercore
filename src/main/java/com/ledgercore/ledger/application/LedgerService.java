package com.ledgercore.ledger.application;

import com.ledgercore.ledger.domain.BalanceSnapshot;
import com.ledgercore.ledger.domain.ChainVerifier;
import com.ledgercore.ledger.domain.EntryType;
import com.ledgercore.ledger.domain.HashChain;
import com.ledgercore.ledger.domain.HistoryCursor;
import com.ledgercore.ledger.domain.Hold;
import com.ledgercore.ledger.domain.InsufficientFundsException;
import com.ledgercore.ledger.domain.IntegrityReport;
import com.ledgercore.ledger.domain.LedgerTransaction;
import com.ledgercore.ledger.domain.PostingCommand;
import com.ledgercore.ledger.domain.PostingLine;
import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.Money;
import com.ledgercore.shared.domain.TransferId;
import com.ledgercore.shared.error.BusinessRuleException;
import com.ledgercore.shared.error.NotFoundException;
import com.ledgercore.shared.error.StateConflictException;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only code that writes postings and balances. Every write method joins the caller's transaction,
 * so the ledger change commits or rolls back together with the transfer that caused it.
 * Insufficient funds is detected before anything is written, which is why it does not have to poison
 * the caller's transaction: the transfer can still be committed as FAILED.
 */
@Service
public class LedgerService {

    private final LedgerRepository repository;

    public LedgerService(LedgerRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void openBalance(AccountId account, Currency currency) {
        repository.createBalance(account, currency, HashChain.GENESIS);
    }

    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = InsufficientFundsException.class)
    public UUID post(PostingCommand command) {
        Map<AccountId, BalanceSnapshot> balances = lockInOrder(command.accountsInLockOrder());
        return apply(command, balances);
    }

    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = InsufficientFundsException.class)
    public void placeHold(TransferId transferId, AccountId account, Money amount) {
        BalanceSnapshot balance = lockInOrder(new TreeSet<>(List.of(account))).get(account);
        requireCurrency(balance, amount.currency());
        if (!balance.canCover(amount)) {
            throw new InsufficientFundsException(account);
        }
        repository.insertHold(new Hold(UUID.randomUUID(), transferId, account, amount, Hold.Status.ACTIVE));
        repository.saveBalance(balance.hold(amount));
    }

    /**
     * Turns an active hold into a posted entry. The hold already reserved the funds, so the debit cannot fail
     * on available balance.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID captureHold(TransferId transferId, AccountId destination, EntryType type) {
        Hold hold = activeHold(transferId);
        PostingCommand command = PostingCommand.move(transferId, type, hold.account(), destination, hold.amount());
        Map<AccountId, BalanceSnapshot> balances = lockInOrder(command.accountsInLockOrder());
        balances.computeIfPresent(hold.account(), (id, balance) -> balance.releaseHold(hold.amount()));
        repository.updateHoldStatus(hold.id(), Hold.Status.CAPTURED);
        return apply(command, balances);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseHold(TransferId transferId) {
        Hold hold = activeHold(transferId);
        BalanceSnapshot balance = lockInOrder(new TreeSet<>(List.of(hold.account()))).get(hold.account());
        repository.updateHoldStatus(hold.id(), Hold.Status.RELEASED);
        repository.saveBalance(balance.releaseHold(hold.amount()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID reverse(TransferId transferId) {
        UUID original = repository.postedEntryFor(transferId)
            .orElseThrow(() -> new StateConflictException("not-posted", "Transfer " + transferId + " has no posted entry"));
        return post(PostingCommand.reversalOf(original, transferId, repository.linesOf(original)));
    }

    @Transactional(readOnly = true)
    public BalanceSnapshot balance(AccountId account) {
        return repository.findBalance(account)
            .orElseThrow(() -> new NotFoundException("account-not-found", "Account " + account + " not found"));
    }

    @Transactional(readOnly = true)
    public HistoryPage history(AccountId account, String cursor, int limit) {
        Long before = cursor == null ? null : HistoryCursor.decode(cursor).beforePostingId();
        List<LedgerTransaction> rows = repository.history(account, before, limit + 1);
        if (rows.size() <= limit) {
            return new HistoryPage(rows, null);
        }
        List<LedgerTransaction> page = rows.subList(0, limit);
        return new HistoryPage(List.copyOf(page), new HistoryCursor(page.getLast().postingId()).encode());
    }

    @Transactional(readOnly = true)
    public IntegrityReport verify(AccountId account) {
        BalanceSnapshot current = balance(account);
        ChainVerifier verifier = new ChainVerifier(account);
        repository.forEachPosting(account, verifier::accept);
        return verifier.finish(current);
    }

    private Map<AccountId, BalanceSnapshot> lockInOrder(SortedSet<AccountId> accounts) {
        Map<AccountId, BalanceSnapshot> locked = new LinkedHashMap<>();
        for (AccountId account : accounts) {
            locked.put(account, repository.lockBalance(account)
                .orElseThrow(() -> new NotFoundException("account-not-found", "Account " + account + " not found")));
        }
        return locked;
    }

    private UUID apply(PostingCommand command, Map<AccountId, BalanceSnapshot> balances) {
        Map<AccountId, BalanceSnapshot> updated = new LinkedHashMap<>();
        for (PostingLine line : command.lines()) {
            BalanceSnapshot balance = balances.get(line.account());
            requireCurrency(balance, line.amount().currency());
            if (line.amount().isNegative() && !balance.canCover(line.amount().negate())) {
                throw new InsufficientFundsException(line.account());
            }
            updated.put(line.account(), balance);
        }

        repository.insertEntry(command);
        for (PostingLine line : command.lines()) {
            BalanceSnapshot before = updated.get(line.account());
            long balanceAfter = Math.addExact(before.balance().minor(), line.amount().minor());
            String hash = HashChain.next(before.lastHash(), command.entryId(), line.account(), line.amount(), balanceAfter);
            repository.insertPosting(command.entryId(), line, balanceAfter, before.lastHash(), hash);
            repository.saveBalance(before.post(line.amount(), hash));
        }
        return command.entryId();
    }

    private Hold activeHold(TransferId transferId) {
        Hold hold = repository.lockHold(transferId)
            .orElseThrow(() -> new StateConflictException("no-hold", "Transfer " + transferId + " has no hold"));
        if (!hold.isActive()) {
            throw new StateConflictException("hold-not-active", "Hold for " + transferId + " is " + hold.status());
        }
        return hold;
    }

    private static void requireCurrency(BalanceSnapshot balance, Currency currency) {
        if (!balance.balance().currency().equals(currency)) {
            throw new BusinessRuleException("currency-mismatch",
                "Account " + balance.account() + " holds " + balance.balance().currencyCode() + ", not " + currency);
        }
    }

    public record HistoryPage(List<LedgerTransaction> items, String nextCursor) {
    }
}
