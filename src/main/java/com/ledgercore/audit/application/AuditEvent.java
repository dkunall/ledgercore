package com.ledgercore.audit.application;

import java.time.Instant;
import java.util.Map;

public record AuditEvent(long id, Instant occurredAt, String actor, String action, String resourceType,
                         String resourceId, Map<String, Object> details, String traceId) {
}
