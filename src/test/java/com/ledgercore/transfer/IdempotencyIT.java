package com.ledgercore.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledgercore.support.IntegrationTest;
import com.ledgercore.support.TestJwt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class IdempotencyIT extends IntegrationTest {

    private final String alice = TestJwt.customer(newSubject());
    private final String bob = TestJwt.customer(newSubject());

    @Test
    void retryWithTheSameKeyReplaysTheFirstResultAndMovesMoneyOnce() {
        UUID from = openFundedAccount(alice, 10_000);
        UUID to = openAccount(bob);
        String key = newKey();
        Map<String, Object> body = body(from, to, 3_000);

        ResponseEntity<JsonNode> first = transfer(alice, key, body);
        ResponseEntity<JsonNode> retry = transfer(alice, key, body);

        assertThat(first.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("false");
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(id(retry)).isEqualTo(id(first));
        assertThat(available(alice, from)).isEqualTo(7_000);
    }

    @Test
    void reusingAKeyForADifferentRequestIsRejected() {
        UUID from = openFundedAccount(alice, 10_000);
        UUID to = openAccount(bob);
        String key = newKey();
        transfer(alice, key, body(from, to, 3_000));

        ResponseEntity<JsonNode> reuse = transfer(alice, key, body(from, to, 4_000));

        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(reuse.getBody().get("code").asText()).isEqualTo("idempotency-key-reused");
        assertThat(available(alice, from)).isEqualTo(7_000);
    }

    @Test
    void keysAreScopedToTheCaller() {
        String carol = TestJwt.customer(newSubject());
        UUID aliceAccount = openFundedAccount(alice, 1_000);
        UUID carolAccount = openFundedAccount(carol, 1_000);
        UUID to = openAccount(bob);
        String key = newKey();

        UUID first = id(transfer(alice, key, body(aliceAccount, to, 100)));
        UUID second = id(transfer(carol, key, body(carolAccount, to, 100)));

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void concurrentDuplicatesProduceExactlyOneTransfer() throws Exception {
        UUID from = openFundedAccount(alice, 10_000);
        UUID to = openAccount(bob);
        String key = newKey();
        Map<String, Object> body = body(from, to, 1_000);
        int callers = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<ResponseEntity<JsonNode>>> calls = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(callers)) {
            for (int i = 0; i < callers; i++) {
                calls.add(CompletableFuture.supplyAsync(() -> {
                    await(start);
                    return transfer(alice, key, body);
                }, pool));
            }
            start.countDown();
            List<ResponseEntity<JsonNode>> responses = calls.stream().map(CompletableFuture::join).toList();

            // Losers either wait for the winner and replay it, or are told the request is still in flight.
            assertThat(responses).allSatisfy(r ->
                assertThat(r.getStatusCode().value()).isIn(201, 409));
            assertThat(responses.stream().filter(r -> r.getStatusCode().value() == 201).map(IntegrationTest::id)
                .distinct()).hasSize(1);
        }
        assertThat(available(alice, from)).isEqualTo(9_000);
        assertThat(jdbc.sql("SELECT count(*) FROM transfer WHERE source_account_id = ? AND type = 'INTERNAL'")
            .param(from).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void missingOrMalformedKeyIsABadRequest() {
        UUID from = openFundedAccount(alice, 1_000);
        UUID to = openAccount(bob);

        assertThat(post(alice, "/v1/transfers", body(from, to, 10)).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(transfer(alice, "short", body(from, to, 10)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static Map<String, Object> body(UUID from, UUID to, long amount) {
        return Map.of("sourceAccountId", from, "destinationAccountId", to, "amountMinor", amount, "currency", "INR");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
