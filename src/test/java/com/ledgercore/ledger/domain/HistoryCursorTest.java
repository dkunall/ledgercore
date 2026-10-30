package com.ledgercore.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgercore.shared.error.InvalidRequestException;
import org.junit.jupiter.api.Test;

class HistoryCursorTest {

    @Test
    void roundTrips() {
        assertThat(HistoryCursor.decode(new HistoryCursor(4_242).encode())).isEqualTo(new HistoryCursor(4_242));
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> HistoryCursor.decode("not-a-cursor")).isInstanceOf(InvalidRequestException.class);
    }
}
