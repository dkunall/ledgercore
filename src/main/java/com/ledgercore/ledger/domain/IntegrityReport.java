package com.ledgercore.ledger.domain;

import com.ledgercore.shared.domain.AccountId;

public record IntegrityReport(AccountId account, boolean valid, long postingsChecked, Long firstBrokenPostingId,
                              String problem) {

    public static IntegrityReport ok(AccountId account, long checked) {
        return new IntegrityReport(account, true, checked, null, null);
    }

    public static IntegrityReport broken(AccountId account, long checked, Long postingId, String problem) {
        return new IntegrityReport(account, false, checked, postingId, problem);
    }
}
