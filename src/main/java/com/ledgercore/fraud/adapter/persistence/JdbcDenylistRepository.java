package com.ledgercore.fraud.adapter.persistence;

import com.ledgercore.fraud.application.DenylistRepository;
import com.ledgercore.fraud.domain.DenylistKind;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcDenylistRepository implements DenylistRepository {

    private final JdbcClient jdbc;

    JdbcDenylistRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean add(DenylistKind kind, String value, String reason, String createdBy) {
        return jdbc.sql("""
                INSERT INTO fraud_denylist (kind, value, reason, created_by) VALUES (:kind, :value, :reason, :by)
                ON CONFLICT DO NOTHING
                """)
            .param("kind", kind.name())
            .param("value", value)
            .param("reason", reason)
            .param("by", createdBy)
            .update() == 1;
    }

    @Override
    public boolean contains(DenylistKind kind, String value) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM fraud_denylist WHERE kind = :kind AND value = :value)")
            .param("kind", kind.name())
            .param("value", value)
            .query(Boolean.class)
            .single();
    }

    @Override
    public List<String> allKeys() {
        return jdbc.sql("SELECT kind || ':' || value FROM fraud_denylist").query(String.class).list();
    }
}
