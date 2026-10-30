package com.ledgercore.transfer.adapter.web;

import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.Actor;
import com.ledgercore.shared.domain.Money;
import com.ledgercore.transfer.application.DepositCommand;
import com.ledgercore.transfer.application.TransferService;
import com.ledgercore.transfer.application.TransferView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
class DepositController {

    private final TransferService transfers;

    DepositController(TransferService transfers) {
        this.transfers = transfers;
    }

    @PostMapping("/v1/accounts/{accountId}/deposits")
    ResponseEntity<TransferView> deposit(Actor actor, @PathVariable UUID accountId,
                                         @RequestHeader("Idempotency-Key") @Pattern(regexp = TransferController.KEY_PATTERN) String key,
                                         @Valid @RequestBody DepositRequest request) {
        DepositCommand command = new DepositCommand(new AccountId(accountId),
            Money.of(request.amountMinor(), request.currency()), request.reference());
        return TransferController.Responses.created(transfers.deposit(actor, key, command));
    }

    record DepositRequest(@Positive long amountMinor, @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency,
                          @Size(max = 140) String reference) {
    }
}
