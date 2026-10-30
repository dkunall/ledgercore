package com.ledgercore.account.adapter.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ledgercore.account.application.AccountService;
import com.ledgercore.ledger.application.LedgerService;
import com.ledgercore.ledger.domain.BalanceSnapshot;
import com.ledgercore.ledger.domain.IntegrityReport;
import com.ledgercore.ledger.domain.LedgerTransaction;
import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.Actor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ledger reads scoped to an account the caller may see. Ownership is checked here, before the ledger
 * (which knows nothing about owners) is asked.
 */
@RestController
@Validated
@RequestMapping("/v1/accounts/{id}")
class AccountLedgerController {

    private final AccountService accounts;
    private final LedgerService ledger;

    AccountLedgerController(AccountService accounts, LedgerService ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    @GetMapping("/balance")
    BalanceResponse balance(Actor actor, @PathVariable UUID id) {
        AccountId account = visible(actor, id);
        BalanceSnapshot b = ledger.balance(account);
        return new BalanceResponse(id, b.balance().currencyCode(), b.balance().minor(), b.held().minor(),
            b.available().minor());
    }

    @GetMapping("/transactions")
    HistoryResponse transactions(Actor actor, @PathVariable UUID id,
                                 @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
                                 @RequestParam(required = false) String cursor) {
        LedgerService.HistoryPage page = ledger.history(visible(actor, id), cursor, limit);
        return new HistoryResponse(page.items().stream().map(TransactionItem::of).toList(), page.nextCursor());
    }

    @GetMapping("/integrity")
    IntegrityResponse integrity(Actor actor, @PathVariable UUID id) {
        IntegrityReport r = ledger.verify(visible(actor, id));
        return new IntegrityResponse(id, r.valid(), r.postingsChecked(), r.firstBrokenPostingId(), r.problem());
    }

    private AccountId visible(Actor actor, UUID id) {
        return accounts.get(actor, new AccountId(id)).id();
    }

    record BalanceResponse(UUID accountId, String currency, long ledgerMinor, long heldMinor, long availableMinor) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record IntegrityResponse(UUID accountId, boolean valid, long postingsChecked, Long firstBrokenPostingId,
                             String problem) {
    }

    record HistoryResponse(List<TransactionItem> items, String nextCursor) {
    }

    record TransactionItem(UUID entryId, UUID transferId, String entryType, long amountMinor, String currency,
                           long balanceAfterMinor, Instant postedAt) {

        static TransactionItem of(LedgerTransaction t) {
            return new TransactionItem(t.entryId(), t.transferId().value(), t.entryType().name(), t.amount().minor(),
                t.amount().currencyCode(), t.balanceAfter().minor(), t.createdAt());
        }
    }
}
