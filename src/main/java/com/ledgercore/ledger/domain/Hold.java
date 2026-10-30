package com.ledgercore.ledger.domain;

import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.Money;
import com.ledgercore.shared.domain.TransferId;
import java.util.UUID;

public record Hold(UUID id, TransferId transferId, AccountId account, Money amount, Status status) {

    public enum Status { ACTIVE, CAPTURED, RELEASED }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }
}
