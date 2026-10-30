package com.ledgercore.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledgercore.support.IntegrationTest;
import com.ledgercore.support.TestJwt;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class ConcurrencyIT extends IntegrationTest {

    private static final int ACCOUNTS = 5;
    private static final int TRANSFERS = 50;
    private static final long OPENING = 10_000;

    @Test
    void fiftyParallelTransfersConserveMoneyAndNeverOverdraw() throws Exception {
        String owner = TestJwt.customer(newSubject());
        List<UUID> accounts = new ArrayList<>();
        for (int i = 0; i < ACCOUNTS; i++) {
            accounts.add(openFundedAccount(owner, OPENING));
        }
        Random random = new Random(42);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<ResponseEntity<JsonNode>>> calls = new ArrayList<>();

        // Amounts are large relative to balances so plenty of transfers fail, and pairs run in both
        // directions so a naive lock order would deadlock.
        try (ExecutorService pool = Executors.newFixedThreadPool(TRANSFERS)) {
            for (int i = 0; i < TRANSFERS; i++) {
                int from = random.nextInt(ACCOUNTS);
                int to = (from + 1 + random.nextInt(ACCOUNTS - 1)) % ACCOUNTS;
                long amount = 1_000 + random.nextInt(4_000);
                calls.add(CompletableFuture.supplyAsync(() -> {
                    awaitQuietly(start);
                    return transfer(owner, accounts.get(from), accounts.get(to), amount);
                }, pool));
            }
            start.countDown();
            List<ResponseEntity<JsonNode>> responses = calls.stream().map(CompletableFuture::join).toList();

            assertThat(responses).allSatisfy(r -> assertThat(r.getStatusCode().value()).isIn(201, 503));
            List<String> statuses = responses.stream().filter(r -> r.getStatusCode().value() == 201)
                .map(r -> r.getBody().get("status").asText()).toList();
            assertThat(statuses).allMatch(s -> s.equals("POSTED") || s.equals("FAILED"));
            assertThat(statuses).contains("POSTED");
        }

        long total = 0;
        for (UUID account : accounts) {
            long balance = ledgerBalance(owner, account);
            assertThat(balance).isNotNegative();
            total += balance;
            assertThat(get(OPS, "/v1/accounts/" + account + "/integrity").getBody().get("valid").asBoolean()).isTrue();
        }
        assertThat(total).isEqualTo(ACCOUNTS * OPENING);
        // Scoped to this test's entries: LedgerIntegrityIT deliberately leaves a tampered row in the shared database.
        assertThat(jdbc.sql("""
                SELECT count(*) FROM (
                    SELECT entry_id FROM posting
                    WHERE entry_id IN (SELECT entry_id FROM posting WHERE account_id = ANY (?))
                    GROUP BY entry_id HAVING sum(amount_minor) <> 0) unbalanced
                """).param(accounts.toArray(UUID[]::new)).query(Long.class).single()).isZero();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
