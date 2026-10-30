package com.ledgercore.transfer.adapter.persistence;

import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.Money;
import com.ledgercore.shared.domain.TransferId;
import com.ledgercore.shared.error.StateConflictException;
import com.ledgercore.transfer.application.TransferRepository;
import com.ledgercore.transfer.domain.Transfer;
import com.ledgercore.transfer.domain.TransferStatus;
import com.ledgercore.transfer.domain.TransferType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTransferRepository implements TransferRepository {

    private static final String SELECT = """
        SELECT id, type, source_account_id, destination_account_id, destination_ref, amount_minor, currency,
               reference, status, failure_reason, initiated_by, approved_by, created_at, updated_at, version
        FROM transfer
        """;

    private final JdbcClient jdbc;

    JdbcTransferRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Transfer t) {
        jdbc.sql("""
                INSERT INTO transfer (id, type, source_account_id, destination_account_id, destination_ref,
                                      amount_minor, currency, reference, status, initiated_by, created_at, updated_at)
                VALUES (:id, :type, :source, :destination, :ref, :amount, :currency, :reference, :status,
                        :initiatedBy, :createdAt, :updatedAt)
                """)
            .param("id", t.id().value())
            .param("type", t.type().name())
            .param("source", t.source().value())
            .param("destination", t.destinationAccount() == null ? null : t.destinationAccount().value())
            .param("ref", t.destinationRef())
            .param("amount", t.amount().minor())
            .param("currency", t.amount().currencyCode())
            .param("reference", t.reference())
            .param("status", t.status().name())
            .param("initiatedBy", t.initiatedBy())
            .param("createdAt", Timestamp.from(t.createdAt()))
            .param("updatedAt", Timestamp.from(t.updatedAt()))
            .update();
    }

    @Override
    public Optional<Transfer> lock(TransferId id) {
        return jdbc.sql(SELECT + " WHERE id = :id FOR UPDATE")
            .param("id", id.value())
            .query(JdbcTransferRepository::map)
            .optional();
    }

    @Override
    public Optional<Transfer> find(TransferId id) {
        return jdbc.sql(SELECT + " WHERE id = :id")
            .param("id", id.value())
            .query(JdbcTransferRepository::map)
            .optional();
    }

    @Override
    public Transfer update(Transfer t) {
        int rows = jdbc.sql("""
                UPDATE transfer
                SET status = :status, failure_reason = :reason, approved_by = :approvedBy, updated_at = :updatedAt,
                    version = version + 1
                WHERE id = :id AND version = :version
                """)
            .param("status", t.status().name())
            .param("reason", t.failureReason())
            .param("approvedBy", t.approvedBy())
            .param("updatedAt", Timestamp.from(t.updatedAt()))
            .param("id", t.id().value())
            .param("version", t.version())
            .update();
        if (rows != 1) {
            throw new StateConflictException("concurrent-modification", "Transfer " + t.id() + " changed concurrently");
        }
        return new Transfer(t.id(), t.type(), t.source(), t.destinationAccount(), t.destinationRef(), t.amount(),
            t.reference(), t.status(), t.failureReason(), t.initiatedBy(), t.approvedBy(), t.createdAt(),
            t.updatedAt(), t.version() + 1);
    }

    @Override
    public List<TransferId> reviewsCreatedBefore(Instant cutoff, int limit) {
        return jdbc.sql("SELECT id FROM transfer WHERE status = 'REVIEW' AND created_at < :cutoff ORDER BY created_at LIMIT :limit")
            .param("cutoff", Timestamp.from(cutoff))
            .param("limit", limit)
            .query((rs, row) -> new TransferId(rs.getObject("id", UUID.class)))
            .list();
    }

    private static Transfer map(ResultSet rs, int row) throws SQLException {
        UUID destination = rs.getObject("destination_account_id", UUID.class);
        return new Transfer(
            new TransferId(rs.getObject("id", UUID.class)),
            TransferType.valueOf(rs.getString("type")),
            new AccountId(rs.getObject("source_account_id", UUID.class)),
            destination == null ? null : new AccountId(destination),
            rs.getString("destination_ref"),
            Money.of(rs.getLong("amount_minor"), rs.getString("currency")),
            rs.getString("reference"),
            TransferStatus.valueOf(rs.getString("status")),
            rs.getString("failure_reason"),
            rs.getString("initiated_by"),
            rs.getString("approved_by"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getLong("version"));
    }
}
