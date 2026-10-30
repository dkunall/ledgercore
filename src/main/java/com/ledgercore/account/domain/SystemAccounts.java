package com.ledgercore.account.domain;

import com.ledgercore.shared.domain.AccountId;
import java.util.UUID;

/**
 * Seeded by V4 migration. Only INR is configured for now.
 */
public final class SystemAccounts {

    public static final AccountId CASH_CLEARING_INR =
        new AccountId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    public static final AccountId RAIL_CLEARING_INR =
        new AccountId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

    private SystemAccounts() {
    }
}
