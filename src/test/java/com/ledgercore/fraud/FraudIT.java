package com.ledgercore.fraud;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledgercore.support.IntegrationTest;
import com.ledgercore.support.TestJwt;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class FraudIT extends IntegrationTest {

    private final String alice = TestJwt.customer(newSubject());
    private final String bob = TestJwt.customer(newSubject());

    @Test
    void denylistedDeviceIsBlockedImmediately() {
        String device = "device-" + UUID.randomUUID();
        ResponseEntity<JsonNode> added = post(OPS, "/v1/fraud/denylist",
            Map.of("kind", "DEVICE", "value", device, "reason", "linked to account takeover"));
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        UUID from = openFundedAccount(alice, 10_000);
        ResponseEntity<JsonNode> blocked = post(alice, "/v1/transfers",
            Map.of("sourceAccountId", from, "destinationAccountId", openAccount(bob), "amountMinor", 100,
                "currency", "INR"),
            Map.of("Idempotency-Key", newKey(), "X-Device-Id", device));

        assertThat(blocked.getBody().get("status").asText()).isEqualTo("REJECTED");
        assertThat(blocked.getBody().get("failureReason").asText()).isEqualTo("fraud-blocked");
        assertThat(available(alice, from)).isEqualTo(10_000);
    }

    @Test
    void addingTheSameEntryTwiceIsHarmless() {
        Map<String, String> entry = Map.of("kind", "DESTINATION", "value", "IFSC:MULE0000001:" + System.nanoTime(),
            "reason", "mule account");

        assertThat(post(OPS, "/v1/fraud/denylist", entry).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(post(OPS, "/v1/fraud/denylist", entry).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void onlyOpsManageTheDenylist() {
        assertThat(post(alice, "/v1/fraud/denylist", Map.of("kind", "DEVICE", "value", "x", "reason", "y"))
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
