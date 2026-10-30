package com.ledgercore.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledgercore.support.IntegrationTest;
import com.ledgercore.support.TestJwt;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class InternalTransferIT extends IntegrationTest {

    private final String alice = TestJwt.customer(newSubject());
    private final String bob = TestJwt.customer(newSubject());

    @Test
    void movesMoneyBetweenAccountsAndRecordsBothSides() {
        UUID from = openFundedAccount(alice, 10_000);
        UUID to = openAccount(bob);

        ResponseEntity<JsonNode> response = transfer(alice, from, to, 2_500);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status").asText()).isEqualTo("POSTED");
        assertThat(response.getHeaders().getLocation()).hasToString("/v1/transfers/" + id(response));
        assertThat(available(alice, from)).isEqualTo(7_500);
        assertThat(available(bob, to)).isEqualTo(2_500);
        assertThat(get(bob, "/v1/transfers/" + id(response)).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void insufficientFundsFailsTheTransferWithoutMovingMoney() {
        UUID from = openFundedAccount(alice, 1_000);
        UUID to = openAccount(bob);

        ResponseEntity<JsonNode> response = transfer(alice, from, to, 1_001);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status").asText()).isEqualTo("FAILED");
        assertThat(response.getBody().get("failureReason").asText()).isEqualTo("insufficient-funds");
        assertThat(available(alice, from)).isEqualTo(1_000);
        assertThat(available(bob, to)).isZero();
    }

    @Test
    void rejectsCurrencyTheAccountDoesNotHold() {
        UUID from = openFundedAccount(alice, 1_000);
        UUID to = openAccount(bob);

        ResponseEntity<JsonNode> response = transfer(alice, newKey(), java.util.Map.of("sourceAccountId", from,
            "destinationAccountId", to, "amountMinor", 10, "currency", "USD"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("code").asText()).isEqualTo("currency-mismatch");
        assertThat(response.getHeaders().getContentType()).hasToString("application/problem+json");
    }

    @Test
    void opsCanReverseAPostedTransfer() {
        UUID from = openFundedAccount(alice, 5_000);
        UUID to = openAccount(bob);
        UUID transfer = id(transfer(alice, from, to, 1_200));

        ResponseEntity<JsonNode> reversed = post(OPS, "/v1/transfers/" + transfer + "/reverse",
            java.util.Map.of("reason", "customer dispute upheld"));

        assertThat(reversed.getBody().get("status").asText()).isEqualTo("REVERSED");
        assertThat(available(alice, from)).isEqualTo(5_000);
        assertThat(available(bob, to)).isZero();
        assertThat(post(OPS, "/v1/transfers/" + transfer + "/reverse", java.util.Map.of("reason", "again"))
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void notifiesBothPartiesThroughKafka() {
        UUID from = openFundedAccount(alice, 5_000);
        UUID to = openAccount(bob);
        UUID transfer = id(transfer(alice, from, to, 700));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
            assertThat(jdbc.sql("SELECT kind FROM notification WHERE transfer_id = ? ORDER BY kind")
                .param(transfer).query(String.class).list()).containsExactly("CREDITED", "DEBITED"));
    }
}
