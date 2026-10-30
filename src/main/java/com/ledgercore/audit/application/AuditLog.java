package com.ledgercore.audit.application;

import com.ledgercore.shared.domain.Actor;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit rows are written in the same transaction as the change they describe, so there is never
 * a committed change without its audit row or an audit row for a change that rolled back.
 */
@Service
public class AuditLog {

    private final AuditRepository repository;
    private final TraceIds traceIds;

    public AuditLog(AuditRepository repository, TraceIds traceIds) {
        this.repository = repository;
        this.traceIds = traceIds;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Actor actor, String action, String resourceType, String resourceId, Map<String, ?> details) {
        repository.append(actor.subject(), action, resourceType, resourceId, details, traceIds.current().orElse(null));
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> forResource(String resourceId, int limit) {
        return repository.findByResource(resourceId, limit);
    }
}
