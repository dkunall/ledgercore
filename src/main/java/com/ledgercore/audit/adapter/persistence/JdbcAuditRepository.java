package com.ledgercore.audit.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.audit.application.AuditEvent;
import com.ledgercore.audit.application.AuditRepository;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAuditRepository implements AuditRepository {

    private static final TypeReference<Map<String, Object>> DETAILS = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    JdbcAuditRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void append(String actor, String action, String resourceType, String resourceId, Map<String, ?> details,
                       String traceId) {
        jdbc.sql("""
                INSERT INTO audit_event (actor, action, resource_type, resource_id, details, trace_id)
                VALUES (:actor, :action, :type, :resource, CAST(:details AS jsonb), :trace)
                """)
            .param("actor", actor)
            .param("action", action)
            .param("type", resourceType)
            .param("resource", resourceId)
            .param("details", write(details))
            .param("trace", traceId)
            .update();
    }

    @Override
    public List<AuditEvent> findByResource(String resourceId, int limit) {
        return jdbc.sql("""
                SELECT id, occurred_at, actor, action, resource_type, resource_id, details::text AS details, trace_id
                FROM audit_event WHERE resource_id = :resource ORDER BY id LIMIT :limit
                """)
            .param("resource", resourceId)
            .param("limit", limit)
            .query((rs, row) -> new AuditEvent(
                rs.getLong("id"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("actor"),
                rs.getString("action"),
                rs.getString("resource_type"),
                rs.getString("resource_id"),
                read(rs.getString("details")),
                rs.getString("trace_id")))
            .list();
    }

    private String write(Map<String, ?> details) {
        try {
            return json.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Audit details are not serialisable", e);
        }
    }

    private Map<String, Object> read(String details) {
        try {
            return json.readValue(details, DETAILS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored audit details are not valid JSON", e);
        }
    }
}
