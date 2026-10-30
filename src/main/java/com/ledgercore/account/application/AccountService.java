package com.ledgercore.account.application;

import com.ledgercore.account.domain.Account;
import com.ledgercore.account.domain.AccountStatus;
import com.ledgercore.account.domain.AccountType;
import com.ledgercore.audit.application.AuditLog;
import com.ledgercore.ledger.application.LedgerService;
import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.Actor;
import com.ledgercore.shared.error.BusinessRuleException;
import com.ledgercore.shared.error.NotFoundException;
import java.time.Clock;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final LedgerService ledger;
    private final AuditLog audit;
    private final Clock clock;
    private final Set<Currency> supportedCurrencies;

    public AccountService(AccountRepository accounts, LedgerService ledger, AuditLog audit, Clock clock,
                          AccountProperties properties) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.audit = audit;
        this.clock = clock;
        this.supportedCurrencies = properties.supportedCurrencies();
    }

    @Transactional
    public Account open(Actor actor, AccountType type, Currency currency) {
        if (type == AccountType.SYSTEM) {
            throw new BusinessRuleException("account-type-not-allowed", "System accounts cannot be opened through the API");
        }
        if (!supportedCurrencies.contains(currency)) {
            throw new BusinessRuleException("currency-not-supported", "Currency " + currency + " is not supported");
        }
        Account account = new Account(AccountId.random(), actor.subject(), type, currency, AccountStatus.ACTIVE,
            clock.instant());
        accounts.insert(account);
        ledger.openBalance(account.id(), currency);
        audit.record(actor, "ACCOUNT_OPENED", "account", account.id().toString(),
            Map.of("type", type.name(), "currency", currency.getCurrencyCode()));
        return account;
    }

    /**
     * Returns 404 rather than 403 for accounts the caller does not own, so ids cannot be probed.
     */
    @Transactional(readOnly = true)
    public Account get(Actor actor, AccountId id) {
        return accounts.findById(id)
            .filter(account -> actor.canAccess(account.ownerId()))
            .orElseThrow(() -> new NotFoundException("account-not-found", "Account " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Account> listOwn(Actor actor) {
        return accounts.findByOwner(actor.subject());
    }

    public Optional<String> ownerOf(AccountId id) {
        return accounts.findById(id).map(Account::ownerId);
    }

    public Account getActive(AccountId id) {
        Account account = accounts.findById(id)
            .orElseThrow(() -> new NotFoundException("account-not-found", "Account " + id + " not found"));
        if (!account.isActive()) {
            throw new BusinessRuleException("account-not-active", "Account " + id + " is " + account.status());
        }
        return account;
    }
}
