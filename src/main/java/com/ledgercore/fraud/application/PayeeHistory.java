package com.ledgercore.fraud.application;

import com.ledgercore.shared.domain.AccountId;

/**
 * Implemented by the transfer module, which owns the data. Keeps fraud from reading transfer tables.
 */
public interface PayeeHistory {

    boolean hasPaidBefore(AccountId source, AccountId destination);
}
