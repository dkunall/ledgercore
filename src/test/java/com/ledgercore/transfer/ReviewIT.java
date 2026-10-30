package com.ledgercore.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledgercore.support.IntegrationTest;
import com.ledgercore.support.TestJwt;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ReviewIT extends IntegrationTest {

    private static final long HIGH_VALUE = 1_000_000;

    private final String alice = TestJwt.customer(newSubject());
    private final String bob = TestJwt.customer(newSubject());

    @Test
    void highValueTransferWaitsForReviewWithFundsHeld() {
        UUID from = openFundedAccount(alice, 2_000_000);
        UUID to = openAccount(bob);

        ResponseEntity<JsonNode> created = transfer(alice, from, to, HIGH_VALUE);

        assertThat(created.getBody().get("status").asText()).isEqualTo("REVIEW");
        assertThat(available(alice, from)).isEqualTo(1_000_000);
        assertThat(available(bob, to)).isZero();
        JsonNode asOps = get(OPS, "/v1/transfers/" + id(created)).getBody();
        assertThat(asOps.at("/fraud/decision").asText()).isEqualTo("REVIEW");
        assertThat(asOps.at("/fraud/reasons").toString()).contains("HIGH_VALUE");
        assertThat(get(alice, "/v1/transfers/" + id(created)).getBody().has("fraud")).isFalse();
    }

    @Test
    void approvalPostsTheHeldFunds() {
        UUID from = openFundedAccount(alice, 2_000_000);
        UUID to = openAccount(bob);
        UUID transfer = id(transfer(alice, from, to, HIGH_VALUE));

        ResponseEntity<JsonNode> approved = post(OPS, "/v1/transfers/" + transfer + "/approve", null);

        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody().get("status").asText()).isEqualTo("POSTED");
        assertThat(ledgerBalance(alice, from)).isEqualTo(1_000_000);
        assertThat(available(alice, from)).isEqualTo(1_000_000);
        assertThat(available(bob, to)).isEqualTo(HIGH_VALUE);
    }

    @Test
    void rejectionReleasesTheHold() {
        UUID from = openFundedAccount(alice, 2_000_000);
        UUID to = openAccount(bob);
        UUID transfer = id(transfer(alice, from, to, HIGH_VALUE));

        ResponseEntity<JsonNode> rejected = post(OPS, "/v1/transfers/" + transfer + "/reject",
            Map.of("reason", "customer could not confirm"));

        assertThat(rejected.getBody().get("status").asText()).isEqualTo("REJECTED");
        assertThat(available(alice, from)).isEqualTo(2_000_000);
        assertThat(post(OPS, "/v1/transfers/" + transfer + "/approve", null).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void makerCannotApproveTheirOwnTransfer() {
        String maker = TestJwt.ops("ops-" + UUID.randomUUID());
        UUID from = openFundedAccount(maker, 2_000_000);
        UUID to = openAccount(bob);
        UUID transfer = id(transfer(maker, from, to, HIGH_VALUE));

        ResponseEntity<JsonNode> selfApproval = post(maker, "/v1/transfers/" + transfer + "/approve", null);

        assertThat(selfApproval.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(selfApproval.getBody().get("code").asText()).isEqualTo("maker-checker");
        assertThat(post(OTHER_OPS, "/v1/transfers/" + transfer + "/approve", null).getBody().get("status").asText())
            .isEqualTo("POSTED");
    }

    @Test
    void customersCannotApprove() {
        UUID from = openFundedAccount(alice, 2_000_000);
        UUID transfer = id(transfer(alice, from, openAccount(bob), HIGH_VALUE));

        assertThat(post(bob, "/v1/transfers/" + transfer + "/approve", null).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
