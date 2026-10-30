package com.ledgercore.idempotency.adapter.persistence;

import com.ledgercore.idempotency.application.IdempotencyStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcIdempotencyStore implements IdempotencyStore {

    private final JdbcClient jdbc;

    JdbcIdempotencyStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean claim(String clientId, String key, String requestHash) {
        return jdbc.sql("""
                INSERT INTO idempotency_record (client_id, idempotency_key, request_hash)
                VALUES (:client, :key, :hash)
                ON CONFLICT DO NOTHING
                """)
            .param("client", clientId)
            .param("key", key)
            .param("hash", requestHash)
            .update() == 1;
    }

    @Override
    public Optional<StoredRequest> find(String clientId, String key) {
        return jdbc.sql("""
                SELECT request_hash, response_status, response_body
                FROM idempotency_record WHERE client_id = :client AND idempotency_key = :key
                """)
            .param("client", clientId)
            .param("key", key)
            .query((rs, row) -> new StoredRequest(rs.getString("request_hash"),
                (Integer) rs.getObject("response_status"), rs.getString("response_body")))
            .optional();
    }

    @Override
    public void complete(String clientId, String key, int status, String body) {
        jdbc.sql("""
                UPDATE idempotency_record SET response_status = :status, response_body = :body
                WHERE client_id = :client AND idempotency_key = :key
                """)
            .param("status", status)
            .param("body", body)
            .param("client", clientId)
            .param("key", key)
            .update();
    }

    @Override
    public int purgeOlderThan(Instant cutoff) {
        return jdbc.sql("DELETE FROM idempotency_record WHERE created_at < :cutoff")
            .param("cutoff", Timestamp.from(cutoff))
            .update();
    }
}
