package com.ledgercore.ledger.application;

import com.ledgercore.ledger.domain.BalanceSnapshot;
import com.ledgercore.ledger.domain.Hold;
import com.ledgercore.ledger.domain.LedgerTransaction;
import com.ledgercore.ledger.domain.PostingCommand;
import com.ledgercore.ledger.domain.PostingLine;
import com.ledgercore.ledger.domain.StoredPosting;
import com.ledgercore.shared.domain.AccountId;
import com.ledgercore.shared.domain.TransferId;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public interface LedgerRepository {

    void createBalance(AccountId account, Currency currency, String genesisHash);

    Optional<BalanceSnapshot> lockBalance(AccountId account);

    Optional<BalanceSnapshot> findBalance(AccountId account);

    void saveBalance(BalanceSnapshot snapshot);

    void insertEntry(PostingCommand command);

    void insertPosting(UUID entryId, PostingLine line, long balanceAfter, String prevHash, String hash);

    List<PostingLine> linesOf(UUID entryId);

    Optional<UUID> postedEntryFor(TransferId transferId);

    void insertHold(Hold hold);

    Optional<Hold> lockHold(TransferId transferId);

    void updateHoldStatus(UUID holdId, Hold.Status status);

    List<LedgerTransaction> history(AccountId account, Long beforePostingId, int limit);

    void forEachPosting(AccountId account, Consumer<StoredPosting> consumer);
}
