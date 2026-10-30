package com.ledgercore.outbox.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer-side half of effectively-once processing: the marker row commits with the consumer's own
 * writes, so a redelivered event is recognised and skipped.
 */
@Service
public class ProcessedEvents {

    private final OutboxRepository repository;

    public ProcessedEvents(OutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(String consumer, UUID eventId) {
        return repository.markProcessed(consumer, eventId);
    }
}
