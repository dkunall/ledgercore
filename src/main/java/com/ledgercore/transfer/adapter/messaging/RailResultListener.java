package com.ledgercore.transfer.adapter.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.shared.domain.TransferId;
import com.ledgercore.shared.messaging.EventEnvelope;
import com.ledgercore.shared.messaging.Topics;
import com.ledgercore.transfer.application.SettlementService;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class RailResultListener {

    private final SettlementService settlement;
    private final ObjectMapper json;

    RailResultListener(SettlementService settlement, ObjectMapper json) {
        this.settlement = settlement;
        this.json = json;
    }

    @KafkaListener(topics = Topics.RAIL_RESULTS, groupId = "ledgercore-transfers")
    void onMessage(String message) throws JsonProcessingException {
        EventEnvelope envelope = json.readValue(message, EventEnvelope.class);
        RailResult result = json.treeToValue(envelope.payload(), RailResult.class);
        settlement.onRailResult(envelope.eventId(), new TransferId(result.transferId()), result.accepted(),
            result.reason());
    }

    record RailResult(UUID transferId, boolean accepted, String railReference, String reason) {
    }
}
