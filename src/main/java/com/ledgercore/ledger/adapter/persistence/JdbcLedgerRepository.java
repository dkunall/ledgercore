package com.ledgercore.ledger.adapter.persistence;

import com.ledgercore.ledger.application.LedgerRepository;
import com.ledgercore.ledger.domain.BalanceSnapshot;
import com.ledgercore.ledger.domain.EntryType;
import com.ledgercore.ledger.domain.Hold;
import com.ledgercore.ledger.domain.LedgerTransaction;
import com.ledgercore.ledger.domain.PostingCommand;
import com.ledgercore.ledger.domain.PostingLine;
import com.ledgercore.ledger.domain.StoredPosting;
import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.Money;
import com.ledgercore.shared.domain.TransferId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLedgerRepository implements LedgerRepository {

    private static final String BALANCE_COLUMNS = "account_id, currency, balance_minor, held_minor, allow_negative, last_hash";

    private final JdbcClient jdbc;

    JdbcLedgerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void createBalance(AccountId account, Currency currency, String genesisHash) {
        jdbc.sql("INSERT INTO account_balance (account_id, currency, last_hash) VALUES (:id, :currency, :hash)")
            .param("id", account.value())
            .param("currency", currency.getCurrencyCode())
            .param("hash", genesisHash)
            .update();
    }

    @Override
    public Optional<BalanceSnapshot> lockBalance(AccountId account) {
        return jdbc.sql("SELECT " + BALANCE_COLUMNS + " FROM account_balance WHERE account_id = :id FOR UPDATE")
            .param("id", account.value())
            .query(JdbcLedgerRepository::mapBalance)
            .optional();
    }

    @Override
    public Optional<BalanceSnapshot> findBalance(AccountId account) {
        return jdbc.sql("SELECT " + BALANCE_COLUMNS + " FROM account_balance WHERE account_id = :id")
            .param("id", account.value())
            .query(JdbcLedgerRepository::mapBalance)
            .optional();
    }

    @Override
    public void saveBalance(BalanceSnapshot snapshot) {
        jdbc.sql("""
                UPDATE account_balance
                SET balance_minor = :balance, held_minor = :held, last_hash = :hash, version = version + 1
                WHERE account_id = :id
                """)
            .param("balance", snapshot.balance().minor())
            .param("held", snapshot.held().minor())
            .param("hash", snapshot.lastHash())
            .param("id", snapshot.account().value())
            .update();
    }

    @Override
    public void insertEntry(PostingCommand command) {
        jdbc.sql("""
                INSERT INTO journal_entry (id, transfer_id, type, reverses_entry_id)
                VALUES (:id, :transfer, :type, :reverses)
                """)
            .param("id", command.entryId())
            .param("transfer", command.transferId().value())
            .param("type", command.type().name())
            .param("reverses", command.reversesEntryId())
            .update();
    }

    @Override
    public void insertPosting(UUID entryId, PostingLine line, long balanceAfter, String prevHash, String hash) {
        jdbc.sql("""
                INSERT INTO posting (entry_id, account_id, amount_minor, currency, balance_after_minor, prev_hash, hash)
                VALUES (:entry, :account, :amount, :currency, :after, :prev, :hash)
                """)
            .param("entry", entryId)
            .param("account", line.account().value())
            .param("amount", line.amount().minor())
            .param("currency", line.amount().currencyCode())
            .param("after", balanceAfter)
            .param("prev", prevHash)
            .param("hash", hash)
            .update();
    }

    @Override
    public List<PostingLine> linesOf(UUID entryId) {
        return jdbc.sql("SELECT account_id, amount_minor, currency FROM posting WHERE entry_id = :entry ORDER BY id")
            .param("entry", entryId)
            .query((rs, row) -> new PostingLine(accountId(rs), money(rs, "amount_minor")))
            .list();
    }

    @Override
    public Optional<UUID> postedEntryFor(TransferId transferId) {
        return jdbc.sql("""
                SELECT id FROM journal_entry
                WHERE transfer_id = :transfer AND type IN ('TRANSFER', 'CAPTURE')
                """)
            .param("transfer", transferId.value())
            .query(UUID.class)
            .optional();
    }

    @Override
    public void insertHold(Hold hold) {
        jdbc.sql("""
                INSERT INTO hold (id, transfer_id, account_id, amount_minor, currency, status)
                VALUES (:id, :transfer, :account, :amount, :currency, :status)
                """)
            .param("id", hold.id())
            .param("transfer", hold.transferId().value())
            .param("account", hold.account().value())
            .param("amount", hold.amount().minor())
            .param("currency", hold.amount().currencyCode())
            .param("status", hold.status().name())
            .update();
    }

    @Override
    public Optional<Hold> lockHold(TransferId transferId) {
        return jdbc.sql("""
                SELECT id, transfer_id, account_id, amount_minor, currency, status
                FROM hold WHERE transfer_id = :transfer FOR UPDATE
                """)
            .param("transfer", transferId.value())
            .query((rs, row) -> new Hold(
                rs.getObject("id", UUID.class),
                new TransferId(rs.getObject("transfer_id", UUID.class)),
                accountId(rs),
                money(rs, "amount_minor"),
                Hold.Status.valueOf(rs.getString("status"))))
            .optional();
    }

    @Override
    public void updateHoldStatus(UUID holdId, Hold.Status status) {
        jdbc.sql("UPDATE hold SET status = :status, resolved_at = now() WHERE id = :id")
            .param("status", status.name())
            .param("id", holdId)
            .update();
    }

    @Override
    public List<LedgerTransaction> history(AccountId account, Long beforePostingId, int limit) {
        return jdbc.sql("""
                SELECT p.id, p.entry_id, e.transfer_id, e.type, p.amount_minor, p.currency,
                       p.balance_after_minor, e.created_at
                FROM posting p
                JOIN journal_entry e ON e.id = p.entry_id
                WHERE p.account_id = :account AND (CAST(:before AS bigint) IS NULL OR p.id < :before)
                ORDER BY p.id DESC
                LIMIT :limit
                """)
            .param("account", account.value())
            .param("before", beforePostingId)
            .param("limit", limit)
            .query((rs, row) -> new LedgerTransaction(
                rs.getLong("id"),
                rs.getObject("entry_id", UUID.class),
                new TransferId(rs.getObject("transfer_id", UUID.class)),
                EntryType.valueOf(rs.getString("type")),
                money(rs, "amount_minor"),
                money(rs, "balance_after_minor"),
                rs.getTimestamp("created_at").toInstant()))
            .list();
    }

    @Override
    public void forEachPosting(AccountId account, Consumer<StoredPosting> consumer) {
        jdbc.sql("""
                SELECT id, entry_id, account_id, amount_minor, currency, balance_after_minor, prev_hash, hash
                FROM posting WHERE account_id = :account ORDER BY id
                """)
            .param("account", account.value())
            .query(rs -> {
                consumer.accept(new StoredPosting(
                    rs.getLong("id"),
                    rs.getObject("entry_id", UUID.class),
                    accountId(rs),
                    money(rs, "amount_minor"),
                    rs.getLong("balance_after_minor"),
                    rs.getString("prev_hash"),
                    rs.getString("hash")));
            });
    }

    private static BalanceSnapshot mapBalance(ResultSet rs, int row) throws SQLException {
        return new BalanceSnapshot(accountId(rs), money(rs, "balance_minor"), money(rs, "held_minor"),
            rs.getBoolean("allow_negative"), rs.getString("last_hash"));
    }

    private static AccountId accountId(ResultSet rs) throws SQLException {
        return new AccountId(rs.getObject("account_id", UUID.class));
    }

    private static Money money(ResultSet rs, String column) throws SQLException {
        return new Money(rs.getLong(column), Currency.getInstance(rs.getString("currency")));
    }
}
