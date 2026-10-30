package com.ledgercore.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;

/**
 * Real Postgres, Redis and Kafka, started once per JVM and shared by every integration test class.
 * Tests isolate themselves by using fresh subjects and accounts rather than truncating tables,
 * which the append-only triggers would refuse anyway.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestJwt.class)
@ActiveProfiles("test")
public abstract class IntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0")
        .withEnv("KAFKA_HEAP_OPTS", "-Xms256m -Xmx256m");

    static {
        Startables.deepStart(POSTGRES, REDIS, KAFKA).join();
    }

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        String url = POSTGRES.getJdbcUrl();
        registry.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?")
            + "options=-c%20lock_timeout=2000%20-c%20statement_timeout=10000");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    protected TestRestTemplate http;

    @Autowired
    protected JdbcClient jdbc;

    protected static final String OPS = TestJwt.ops("ops-" + UUID.randomUUID());
    protected static final String OTHER_OPS = TestJwt.ops("ops-" + UUID.randomUUID());

    protected ResponseEntity<JsonNode> get(String token, String path) {
        return exchange(HttpMethod.GET, token, path, null, Map.of());
    }

    protected ResponseEntity<JsonNode> post(String token, String path, Object body) {
        return exchange(HttpMethod.POST, token, path, body, Map.of());
    }

    protected ResponseEntity<JsonNode> post(String token, String path, Object body, Map<String, String> headers) {
        return exchange(HttpMethod.POST, token, path, body, headers);
    }

    protected ResponseEntity<JsonNode> exchange(HttpMethod method, String token, String path, Object body,
                                                Map<String, String> extraHeaders) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        extraHeaders.forEach(headers::set);
        return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    protected UUID openAccount(String token) {
        ResponseEntity<JsonNode> response = post(token, "/v1/accounts", Map.of("type", "SAVINGS", "currency", "INR"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(response.getBody().get("id").asText());
    }

    protected UUID openFundedAccount(String token, long amountMinor) {
        UUID account = openAccount(token);
        deposit(account, amountMinor);
        return account;
    }

    protected void deposit(UUID account, long amountMinor) {
        ResponseEntity<JsonNode> response = post(OPS, "/v1/accounts/" + account + "/deposits",
            Map.of("amountMinor", amountMinor, "currency", "INR", "reference", "test funding"),
            Map.of("Idempotency-Key", newKey()));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    protected ResponseEntity<JsonNode> transfer(String token, UUID from, UUID to, long amountMinor) {
        return transfer(token, newKey(), Map.of("sourceAccountId", from, "destinationAccountId", to,
            "amountMinor", amountMinor, "currency", "INR"));
    }

    protected ResponseEntity<JsonNode> transfer(String token, String key, Map<String, Object> body) {
        return post(token, "/v1/transfers", body, Map.of("Idempotency-Key", key));
    }

    protected long available(String token, UUID account) {
        return get(token, "/v1/accounts/" + account + "/balance").getBody().get("availableMinor").asLong();
    }

    protected long ledgerBalance(String token, UUID account) {
        return get(token, "/v1/accounts/" + account + "/balance").getBody().get("ledgerMinor").asLong();
    }

    protected String status(String token, UUID transfer) {
        return get(token, "/v1/transfers/" + transfer).getBody().get("status").asText();
    }

    protected static String newKey() {
        return "k-" + UUID.randomUUID();
    }

    protected static String newSubject() {
        return "user-" + UUID.randomUUID();
    }

    protected static UUID id(ResponseEntity<JsonNode> response) {
        return UUID.fromString(response.getBody().get("id").asText());
    }
}
