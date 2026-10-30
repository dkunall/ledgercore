package com.ledgercore.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledgercore.support.IntegrationTest;
import com.ledgercore.support.TestJwt;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;

class LedgerIntegrityIT extends IntegrationTest {

    @Autowired
    private TransactionTemplate tx;

    private final String alice = TestJwt.customer(newSubject());
    private final String bob = TestJwt.customer(newSubject());

    @Test
    void historyPagesNewestFirstWithoutGapsOrRepeats() {
        UUID from = openFundedAccount(alice, 100_000);
        UUID to = openAccount(bob);
        for (int i = 1; i <= 7; i++) {
            transfer(alice, from, to, i * 100L);
        }

        List<Long> amounts = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            String path = "/v1/accounts/" + from + "/transactions?limit=3" + (cursor == null ? "" : "&cursor=" + cursor);
            JsonNode page = get(alice, path).getBody();
            page.get("items").forEach(item -> amounts.add(item.get("amountMinor").asLong()));
            cursor = page.hasNonNull("nextCursor") ? page.get("nextCursor").asText() : null;
            pages++;
        } while (cursor != null);

        assertThat(pages).isEqualTo(3);
        assertThat(amounts).containsExactly(-700L, -600L, -500L, -400L, -300L, -200L, -100L, 100_000L);
    }

    @Test
    void badCursorAndLimitAreRejected() {
        UUID account = openAccount(alice);

        assertThat(get(alice, "/v1/accounts/" + account + "/transactions?cursor=zzz").getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get(alice, "/v1/accounts/" + account + "/transactions?limit=5000").getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void postingsCannotBeUpdatedOrDeleted() {
        UUID account = openFundedAccount(alice, 1_000);

        assertThatThrownBy(() -> jdbc.sql("UPDATE posting SET amount_minor = 1 WHERE account_id = ?")
            .param(account).update()).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM posting WHERE account_id = ?")
            .param(account).update()).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    @Test
    void unbalancedEntriesAreRefusedByTheDatabase() {
        UUID account = openAccount(alice);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            UUID entry = UUID.randomUUID();
            jdbc.sql("INSERT INTO journal_entry (id, transfer_id, type) VALUES (?, ?, 'TRANSFER')")
                .params(entry, UUID.randomUUID()).update();
            jdbc.sql("""
                    INSERT INTO posting (entry_id, account_id, amount_minor, currency, balance_after_minor, prev_hash, hash)
                    VALUES (?, ?, 500, 'INR', 500, 'x', 'y')
                    """).params(entry, account).update();
        })).isInstanceOf(DataAccessException.class).hasStackTraceContaining("unbalanced");
    }

    @Test
    void integrityCheckFindsAPostingEditedBehindTheApplicationsBack() {
        UUID from = openFundedAccount(alice, 10_000);
        UUID to = openAccount(bob);
        transfer(alice, from, to, 1_000);
        transfer(alice, from, to, 2_000);
        assertThat(get(OPS, "/v1/accounts/" + from + "/integrity").getBody().get("valid").asBoolean()).isTrue();

        // Simulates someone with table-owner rights bypassing the append-only trigger.
        Long tampered = tx.execute(status -> {
            jdbc.sql("ALTER TABLE posting DISABLE TRIGGER posting_append_only").update();
            Long id = jdbc.sql("""
                    UPDATE posting SET amount_minor = -10 WHERE id = (
                        SELECT min(id) FROM posting WHERE account_id = ? AND amount_minor = -1000)
                    RETURNING id
                    """).param(from).query(Long.class).single();
            jdbc.sql("ALTER TABLE posting ENABLE TRIGGER posting_append_only").update();
            return id;
        });

        JsonNode report = get(OPS, "/v1/accounts/" + from + "/integrity").getBody();
        assertThat(report.get("valid").asBoolean()).isFalse();
        assertThat(report.get("firstBrokenPostingId").asLong()).isEqualTo(tampered);
        assertThat(report.get("problem").asText()).contains("hash");
    }

    @Test
    void everyStateChangeLeavesAnAuditEvent() {
        UUID from = openFundedAccount(alice, 10_000);
        UUID transfer = id(transfer(alice, from, openAccount(bob), 1_000));

        JsonNode events = get(OPS, "/v1/audit?resourceId=" + transfer).getBody();

        assertThat(events.findValuesAsText("action")).contains("TRANSFER_CREATED");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM audit_event").update())
            .isInstanceOf(DataAccessException.class);
    }
}
