package com.ledgercore.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.shared.messaging.EventEnvelope;
import com.ledgercore.shared.messaging.Topics;
import com.ledgercore.support.IntegrationTest;
import com.ledgercore.support.TestJwt;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * The payout saga: hold funds, ask the rail over Kafka, then capture or release on its answer.
 */
class ExternalPayoutIT extends IntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private ObjectMapper json;

    private final String alice = TestJwt.customer(newSubject());

    @Test
    void acceptedPayoutCapturesTheHold() {
        UUID from = openFundedAccount(alice, 10_000);

        ResponseEntity<JsonNode> created = payout(from, "IFSC:HDFC0001234:5012", 4_000);

        assertThat(created.getBody().get("status").asText()).isEqualTo("HELD");
        assertThat(available(alice, from)).isEqualTo(6_000);
        assertThat(ledgerBalance(alice, from)).isEqualTo(10_000);

        UUID transfer = id(created);
        await().atMost(Duration.ofSeconds(30)).until(() -> status(alice, transfer).equals("POSTED"));
        assertThat(available(alice, from)).isEqualTo(6_000);
        assertThat(ledgerBalance(alice, from)).isEqualTo(6_000);
    }

    @Test
    void rejectedPayoutReleasesTheHold() {
        UUID from = openFundedAccount(alice, 10_000);

        UUID transfer = id(payout(from, "IFSC:HDFC0001234:9000", 4_000));

        await().atMost(Duration.ofSeconds(30)).until(() -> status(alice, transfer).equals("FAILED"));
        assertThat(get(alice, "/v1/transfers/" + transfer).getBody().get("failureReason").asText())
            .startsWith("rail-rejected");
        assertThat(available(alice, from)).isEqualTo(10_000);
        assertThat(ledgerBalance(alice, from)).isEqualTo(10_000);
    }

    @Test
    void redeliveredRailResultIsIgnored() throws Exception {
        UUID from = openFundedAccount(alice, 10_000);
        UUID transfer = id(payout(from, "IFSC:HDFC0001234:7777", 1_000));
        await().atMost(Duration.ofSeconds(30)).until(() -> status(alice, transfer).equals("POSTED"));

        Map<String, Object> row = jdbc.sql("""
                SELECT event_id, event_type, payload::text AS payload, created_at FROM outbox
                WHERE topic = ? AND message_key = ?
                """).params(Topics.RAIL_RESULTS, transfer.toString()).query().singleRow();
        EventEnvelope original = new EventEnvelope((UUID) row.get("event_id"), (String) row.get("event_type"),
            ((java.sql.Timestamp) row.get("created_at")).toInstant(), json.readTree((String) row.get("payload")));
        kafka.send(Topics.RAIL_RESULTS, transfer.toString(), json.writeValueAsString(original)).get();

        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(ledgerBalance(alice, from)).isEqualTo(9_000);
            assertThat(jdbc.sql("SELECT count(*) FROM journal_entry WHERE transfer_id = ?").param(transfer)
                .query(Long.class).single()).isEqualTo(1);
        });
    }

    private ResponseEntity<JsonNode> payout(UUID from, String destinationRef, long amount) {
        return transfer(alice, newKey(), Map.of("sourceAccountId", from, "destinationRef", destinationRef,
            "amountMinor", amount, "currency", "INR"));
    }
}
