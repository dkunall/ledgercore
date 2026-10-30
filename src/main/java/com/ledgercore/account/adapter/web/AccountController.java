package com.ledgercore.account.adapter.web;

import com.ledgercore.account.application.AccountService;
import com.ledgercore.account.domain.Account;
import com.ledgercore.account.domain.AccountType;
import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/accounts")
class AccountController {

    private final AccountService accounts;

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping
    ResponseEntity<AccountResponse> open(Actor actor, @Valid @RequestBody OpenAccountRequest request) {
        Account account = accounts.open(actor, request.type(), Currency.getInstance(request.currency()));
        return ResponseEntity.created(URI.create("/v1/accounts/" + account.id())).body(AccountResponse.of(account));
    }

    @GetMapping
    List<AccountResponse> list(Actor actor) {
        return accounts.listOwn(actor).stream().map(AccountResponse::of).toList();
    }

    @GetMapping("/{id}")
    AccountResponse get(Actor actor, @PathVariable UUID id) {
        return AccountResponse.of(accounts.get(actor, new AccountId(id)));
    }

    record OpenAccountRequest(@NotNull AccountType type, @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency) {
    }

    record AccountResponse(UUID id, String ownerId, AccountType type, String currency, String status,
                           Instant createdAt) {

        static AccountResponse of(Account a) {
            return new AccountResponse(a.id().value(), a.ownerId(), a.type(), a.currency().getCurrencyCode(),
                a.status().name(), a.createdAt());
        }
    }
}
