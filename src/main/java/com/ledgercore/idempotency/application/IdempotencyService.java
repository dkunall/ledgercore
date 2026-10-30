package com.ledgercore.idempotency.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.shared.error.BusinessRuleException;
import com.ledgercore.shared.error.StateConflictException;
import java.util.Optional;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Key and outcome are stored in the caller's transaction. Either the request's effects and its stored
 * response commit together, or neither does and a retry runs the request from scratch.
 */
@Service
public class IdempotencyService {

    private final IdempotencyStore store;
    private final ObjectMapper json;

    public IdempotencyService(IdempotencyStore store, ObjectMapper json) {
        this.store = store;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public <T> Optional<T> begin(String clientId, String key, String requestHash, Class<T> responseType) {
        boolean claimed;
        try {
            claimed = store.claim(clientId, key, requestHash);
        } catch (PessimisticLockingFailureException e) {
            throw new StateConflictException("request-in-progress",
                "A request with this Idempotency-Key is still being processed");
        }
        if (claimed) {
            return Optional.empty();
        }
        IdempotencyStore.StoredRequest stored = store.find(clientId, key)
            .orElseThrow(() -> new IllegalStateException("Idempotency record vanished after conflict"));
        if (!stored.requestHash().equals(requestHash)) {
            throw new BusinessRuleException("idempotency-key-reused",
                "Idempotency-Key was already used with a different request body");
        }
        if (stored.body() == null) {
            throw new StateConflictException("request-in-progress",
                "A request with this Idempotency-Key is still being processed");
        }
        return Optional.of(read(stored.body(), responseType));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(String clientId, String key, int status, Object response) {
        try {
            store.complete(clientId, key, status, json.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Response is not serialisable", e);
        }
    }

    private <T> T read(String body, Class<T> type) {
        try {
            return json.readValue(body, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored response is not readable", e);
        }
    }
}
