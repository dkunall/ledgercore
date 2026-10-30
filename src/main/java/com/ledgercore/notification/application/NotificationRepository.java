package com.ledgercore.notification.application;

import java.util.UUID;

public interface NotificationRepository {

    void insertIfAbsent(UUID accountId, UUID transferId, String kind, String message);
}
