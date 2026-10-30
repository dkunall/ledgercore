package com.ledgercore.transfer.adapter.persistence;

import com.ledgercore.fraud.application.PayeeHistory;
import com.ledgercore.shared.domain.AccountId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class JdbcPayeeHistory implements PayeeHistory {

    private final JdbcClient jdbc;

    JdbcPayeeHistory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean hasPaidBefore(AccountId source, AccountId destination) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM transfer
                               WHERE source_account_id = :source AND destination_account_id = :destination
                                 AND status = 'POSTED')
                """)
            .param("source", source.value())
            .param("destination", destination.value())
            .query(Boolean.class)
            .single();
    }
}
