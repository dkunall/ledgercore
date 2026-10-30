package com.ledgercore.audit.application;

import java.util.List;
import java.util.Map;

public interface AuditRepository {

    void append(String actor, String action, String resourceType, String resourceId, Map<String, ?> details,
                String traceId);

    List<AuditEvent> findByResource(String resourceId, int limit);
}
