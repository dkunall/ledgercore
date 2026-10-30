package com.ledgercore.account.adapter.persistence;

import com.ledgercore.account.application.AccountRepository;
import com.ledgercore.account.domain.Account;
import com.ledgercore.account.domain.AccountStatus;
import com.ledgercore.account.domain.AccountType;
import com.ledgercore.shared.domain.AccountId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAccountRepository implements AccountRepository {

    private static final String COLUMNS = "id, owner_id, type, currency, status, created_at";

    private final JdbcClient jdbc;

    JdbcAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Account account) {
        jdbc.sql("""
                INSERT INTO account (id, owner_id, type, currency, status, created_at)
                VALUES (:id, :owner, :type, :currency, :status, :createdAt)
                """)
            .param("id", account.id().value())
            .param("owner", account.ownerId())
            .param("type", account.type().name())
            .param("currency", account.currency().getCurrencyCode())
            .param("status", account.status().name())
            .param("createdAt", Timestamp.from(account.createdAt()))
            .update();
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM account WHERE id = :id")
            .param("id", id.value())
            .query(JdbcAccountRepository::map)
            .optional();
    }

    @Override
    public List<Account> findByOwner(String ownerId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM account WHERE owner_id = :owner ORDER BY created_at")
            .param("owner", ownerId)
            .query(JdbcAccountRepository::map)
            .list();
    }

    private static Account map(ResultSet rs, int row) throws SQLException {
        return new Account(
            new AccountId(rs.getObject("id", UUID.class)),
            rs.getString("owner_id"),
            AccountType.valueOf(rs.getString("type")),
            Currency.getInstance(rs.getString("currency")),
            AccountStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at").toInstant());
    }
}
