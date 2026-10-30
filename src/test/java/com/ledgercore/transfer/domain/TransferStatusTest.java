package com.ledgercore.transfer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TransferStatusTest {

    @ParameterizedTest
    @CsvSource({
        "INITIATED, POSTED, true",
        "INITIATED, REVIEW, true",
        "REVIEW, POSTED, true",
        "REVIEW, HELD, true",
        "HELD, POSTED, true",
        "HELD, FAILED, true",
        "POSTED, REVERSED, true",
        "POSTED, FAILED, false",
        "HELD, REVIEW, false",
        "REJECTED, POSTED, false",
        "REVERSED, POSTED, false"
    })
    void allowsOnlyForwardTransitions(TransferStatus from, TransferStatus to, boolean allowed) {
        assertThat(from.canMoveTo(to)).isEqualTo(allowed);
    }

    @Test
    void terminalStatesHaveNoWayOut() {
        assertThat(TransferStatus.REJECTED.isTerminal()).isTrue();
        assertThat(TransferStatus.FAILED.isTerminal()).isTrue();
        assertThat(TransferStatus.REVERSED.isTerminal()).isTrue();
        assertThat(TransferStatus.POSTED.isTerminal()).isFalse();
    }
}
