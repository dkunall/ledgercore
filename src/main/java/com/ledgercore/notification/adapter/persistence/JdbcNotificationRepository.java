package com.ledgercore.notification.adapter.persistence;

import com.ledgercore.notification.application.NotificationRepository;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcNotificationRepository implements NotificationRepository {

    private final JdbcClient jdbc;

    JdbcNotificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertIfAbsent(UUID accountId, UUID transferId, String kind, String message) {
        jdbc.sql("""
                INSERT INTO notification (account_id, transfer_id, kind, message)
                VALUES (:account, :transfer, :kind, :message)
                ON CONFLICT DO NOTHING
                """)
            .param("account", accountId)
            .param("transfer", transferId)
            .param("kind", kind)
            .param("message", message)
            .update();
    }
}
