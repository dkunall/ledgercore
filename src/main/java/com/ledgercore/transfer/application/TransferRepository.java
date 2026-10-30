package com.ledgercore.transfer.application;

import com.ledgercore.shared.domain.TransferId;
import com.ledgercore.transfer.domain.Transfer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransferRepository {

    void insert(Transfer transfer);

    Optional<Transfer> lock(TransferId id);

    Optional<Transfer> find(TransferId id);

    /**
     * @return the stored transfer with its new version
     */
    Transfer update(Transfer transfer);

    List<TransferId> reviewsCreatedBefore(Instant cutoff, int limit);
}
