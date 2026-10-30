package com.ledgercore.transfer.domain;

import com.ledgercore.shared.error.StateConflictException;

public class IllegalTransitionException extends StateConflictException {

    public IllegalTransitionException(TransferStatus from, TransferStatus to) {
        super("illegal-transition", "Transfer cannot move from " + from + " to " + to);
    }
}
