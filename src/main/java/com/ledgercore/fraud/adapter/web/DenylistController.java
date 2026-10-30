package com.ledgercore.fraud.adapter.web;

import com.ledgercore.fraud.application.FraudService;
import com.ledgercore.fraud.domain.DenylistKind;
import com.ledgercore.shared.domain.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/fraud/denylist")
class DenylistController {

    private final FraudService fraud;

    DenylistController(FraudService fraud) {
        this.fraud = fraud;
    }

    @PostMapping
    ResponseEntity<DenylistResponse> add(Actor actor, @Valid @RequestBody DenylistRequest request) {
        boolean added = fraud.deny(actor, request.kind(), request.value(), request.reason());
        return ResponseEntity.status(added ? HttpStatus.CREATED : HttpStatus.OK)
            .body(new DenylistResponse(request.kind(), request.value(), added));
    }

    record DenylistRequest(@NotNull DenylistKind kind, @NotBlank @Size(max = 128) String value,
                           @NotBlank @Size(max = 256) String reason) {
    }

    record DenylistResponse(DenylistKind kind, String value, boolean added) {
    }
}
