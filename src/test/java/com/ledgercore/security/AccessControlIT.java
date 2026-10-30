package com.ledgercore.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledgercore.support.IntegrationTest;
import com.ledgercore.support.TestJwt;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AccessControlIT extends IntegrationTest {

    private final String alice = TestJwt.customer(newSubject());
    private final String mallory = TestJwt.customer(newSubject());

    @Test
    void requestsWithoutATokenAreUnauthorized() {
        ResponseEntity<JsonNode> response = get(null, "/v1/accounts");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code").asText()).isEqualTo("unauthenticated");
    }

    @Test
    void tokensWithABrokenSignatureAreRejected() {
        String token = TestJwt.customer(newSubject());
        String forged = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");

        assertThat(get(forged, "/v1/accounts").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void readScopeCannotWrite() {
        String reader = TestJwt.readOnly(newSubject());

        assertThat(get(reader, "/v1/accounts").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post(reader, "/v1/accounts", Map.of("type", "SAVINGS", "currency", "INR")).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void someoneElsesAccountLooksLikeItDoesNotExist() {
        UUID account = openFundedAccount(alice, 5_000);

        assertThat(get(mallory, "/v1/accounts/" + account).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(mallory, "/v1/accounts/" + account + "/balance").getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(mallory, "/v1/accounts/" + account + "/transactions").getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cannotSpendFromSomeoneElsesAccount() {
        UUID victim = openFundedAccount(alice, 5_000);
        UUID thief = openAccount(mallory);

        ResponseEntity<JsonNode> response = transfer(mallory, victim, thief, 5_000);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(available(alice, victim)).isEqualTo(5_000);
    }

    @Test
    void customersCannotDepositOrReadTheAuditTrail() {
        UUID account = openAccount(alice);

        assertThat(post(alice, "/v1/accounts/" + account + "/deposits",
            Map.of("amountMinor", 100, "currency", "INR"), Map.of("Idempotency-Key", newKey())).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(alice, "/v1/audit?resourceId=" + account).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void systemAccountsCannotBeOpenedByCustomers() {
        ResponseEntity<JsonNode> response = post(alice, "/v1/accounts", Map.of("type", "SYSTEM", "currency", "INR"));

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void healthIsPublic() {
        assertThat(get(null, "/actuator/health").getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
