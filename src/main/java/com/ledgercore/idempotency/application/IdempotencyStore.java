package com.ledgercore.idempotency.application;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyStore {

    /**
     * Inserts a placeholder for the key. Blocks while another transaction holds an uncommitted placeholder
     * for the same key, so two concurrent requests with one key are serialised by the database.
     *
     * @return true if this call created the placeholder
     */
    boolean claim(String clientId, String key, String requestHash);

    Optional<StoredRequest> find(String clientId, String key);

    void complete(String clientId, String key, int status, String body);

    int purgeOlderThan(Instant cutoff);

    record StoredRequest(String requestHash, Integer status, String body) {
    }
}
