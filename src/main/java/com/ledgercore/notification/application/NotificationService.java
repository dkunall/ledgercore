package com.ledgercore.notification.application;

import com.ledgercore.outbox.application.ProcessedEvents;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records customer notifications. Delivery (SMS, push, email) is out of scope; this is the point where
 * a real sender would pick them up.
 */
@Service
public class NotificationService {

    static final String CONSUMER = "notifications";

    private final NotificationRepository notifications;
    private final ProcessedEvents processed;

    public NotificationService(NotificationRepository notifications, ProcessedEvents processed) {
        this.notifications = notifications;
        this.processed = processed;
    }

    @Transactional
    public void onTransferStatus(UUID eventId, TransferStatusEvent event) {
        if (!processed.firstTime(CONSUMER, eventId)) {
            return;
        }
        String amount = event.amountMinor() + " " + event.currency() + " (minor units)";
        switch (event.status()) {
            case "POSTED" -> {
                notifications.insertIfAbsent(event.sourceAccountId(), event.transferId(), "DEBITED",
                    "Debited " + amount);
                if (event.destinationAccountId() != null) {
                    notifications.insertIfAbsent(event.destinationAccountId(), event.transferId(), "CREDITED",
                        "Credited " + amount);
                }
            }
            case "FAILED", "REJECTED" -> notifications.insertIfAbsent(event.sourceAccountId(), event.transferId(),
                event.status(), "Transfer of " + amount + " did not go through: " + event.failureReason());
            case "REVERSED" -> notifications.insertIfAbsent(event.sourceAccountId(), event.transferId(), "REVERSED",
                "Transfer of " + amount + " was reversed");
            default -> {
                // REVIEW and HELD are internal states the customer is not told about.
            }
        }
    }

    public record TransferStatusEvent(UUID transferId, String type, String status, UUID sourceAccountId,
                                      UUID destinationAccountId, long amountMinor, String currency,
                                      String failureReason) {
    }
}
