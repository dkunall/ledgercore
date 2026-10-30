package com.ledgercore.fraud.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.fraud.application.FraudDecisionRepository;
import com.ledgercore.fraud.domain.Decision;
import com.ledgercore.fraud.domain.FraudAssessment;
import com.ledgercore.shared.domain.TransferId;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcFraudDecisionRepository implements FraudDecisionRepository {

    private static final TypeReference<List<String>> REASONS = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    JdbcFraudDecisionRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void save(TransferId transferId, FraudAssessment assessment) {
        try {
            jdbc.sql("""
                    INSERT INTO fraud_decision (transfer_id, decision, score, reasons, degraded)
                    VALUES (:transfer, :decision, :score, CAST(:reasons AS jsonb), :degraded)
                    """)
                .param("transfer", transferId.value())
                .param("decision", assessment.decision().name())
                .param("score", assessment.score())
                .param("reasons", json.writeValueAsString(assessment.reasons()))
                .param("degraded", assessment.degraded())
                .update();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Optional<FraudAssessment> find(TransferId transferId) {
        return jdbc.sql("SELECT decision, score, reasons::text AS reasons, degraded FROM fraud_decision WHERE transfer_id = :id")
            .param("id", transferId.value())
            .query((rs, row) -> new FraudAssessment(Decision.valueOf(rs.getString("decision")), rs.getInt("score"),
                readReasons(rs.getString("reasons")), rs.getBoolean("degraded")))
            .optional();
    }

    private List<String> readReasons(String raw) {
        try {
            return json.readValue(raw, REASONS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
