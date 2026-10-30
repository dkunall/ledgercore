package com.ledgercore.notification.adapter.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.notification.application.NotificationService;
import com.ledgercore.shared.messaging.EventEnvelope;
import com.ledgercore.shared.messaging.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class TransferEventListener {

    private final NotificationService notifications;
    private final ObjectMapper json;

    TransferEventListener(NotificationService notifications, ObjectMapper json) {
        this.notifications = notifications;
        this.json = json;
    }

    @KafkaListener(topics = Topics.TRANSFERS, groupId = "ledgercore-notifications")
    void onMessage(String message) throws JsonProcessingException {
        EventEnvelope envelope = json.readValue(message, EventEnvelope.class);
        notifications.onTransferStatus(envelope.eventId(),
            json.treeToValue(envelope.payload(), NotificationService.TransferStatusEvent.class));
    }
}
