package com.ledgercore.transfer.adapter.web;

import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.Actor;
import com.ledgercore.shared.domain.Money;
import com.ledgercore.shared.domain.TransferId;
import com.ledgercore.transfer.application.CreateTransferCommand;
import com.ledgercore.transfer.application.ReviewService;
import com.ledgercore.transfer.application.SettlementService;
import com.ledgercore.transfer.application.TransferResult;
import com.ledgercore.transfer.application.TransferService;
import com.ledgercore.transfer.application.TransferView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/v1/transfers")
class TransferController {

    static final String KEY_PATTERN = "^[A-Za-z0-9_-]{8,64}$";

    private final TransferService transfers;
    private final ReviewService reviews;
    private final SettlementService settlement;

    TransferController(TransferService transfers, ReviewService reviews, SettlementService settlement) {
        this.transfers = transfers;
        this.reviews = reviews;
        this.settlement = settlement;
    }

    @PostMapping
    ResponseEntity<TransferView> create(Actor actor,
                                        @RequestHeader("Idempotency-Key") @Pattern(regexp = KEY_PATTERN) String key,
                                        @RequestHeader(value = "X-Device-Id", required = false) @Size(max = 128) String deviceId,
                                        @Valid @RequestBody CreateTransferRequest request) {
        TransferResult result = transfers.create(actor, key, request.toCommand(deviceId));
        return Responses.created(result);
    }

    @GetMapping("/{id}")
    TransferView get(Actor actor, @PathVariable UUID id) {
        return transfers.get(actor, new TransferId(id));
    }

    @PostMapping("/{id}/approve")
    TransferView approve(Actor actor, @PathVariable UUID id) {
        return reviews.approve(actor, new TransferId(id));
    }

    @PostMapping("/{id}/reject")
    TransferView reject(Actor actor, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return reviews.reject(actor, new TransferId(id), request.reason());
    }

    @PostMapping("/{id}/reverse")
    TransferView reverse(Actor actor, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return settlement.reverse(actor, new TransferId(id), request.reason());
    }

    record CreateTransferRequest(
        @NotNull UUID sourceAccountId,
        UUID destinationAccountId,
        @Pattern(regexp = "^[A-Z0-9:-]{4,64}$") String destinationRef,
        @Positive long amountMinor,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @Size(max = 140) String reference) {

        @AssertTrue(message = "exactly one of destinationAccountId or destinationRef is required")
        boolean isSingleDestination() {
            return (destinationAccountId == null) != (destinationRef == null);
        }

        @AssertTrue(message = "source and destination must differ")
        boolean isDistinctAccounts() {
            return destinationAccountId == null || !destinationAccountId.equals(sourceAccountId);
        }

        CreateTransferCommand toCommand(String deviceId) {
            return new CreateTransferCommand(new AccountId(sourceAccountId),
                destinationAccountId == null ? null : new AccountId(destinationAccountId), destinationRef,
                Money.of(amountMinor, currency), reference, deviceId);
        }
    }

    record ReasonRequest(@NotBlank @Size(max = 256) String reason) {
    }

    static final class Responses {

        private Responses() {
        }

        static ResponseEntity<TransferView> created(TransferResult result) {
            return ResponseEntity.created(URI.create("/v1/transfers/" + result.transfer().id()))
                .header("Idempotent-Replayed", Boolean.toString(result.replayed()))
                .body(result.transfer());
        }
    }
}
