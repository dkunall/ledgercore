package com.ledgercore.rail.adapter.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.rail.application.RailGateway;
import com.ledgercore.shared.messaging.EventEnvelope;
import com.ledgercore.shared.messaging.Topics;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class RailRequestListener {

    private final RailGateway gateway;
    private final ObjectMapper json;

    RailRequestListener(RailGateway gateway, ObjectMapper json) {
        this.gateway = gateway;
        this.json = json;
    }

    @KafkaListener(topics = Topics.RAIL_REQUESTS, groupId = "ledgercore-rail")
    void onMessage(String message) throws JsonProcessingException {
        EventEnvelope envelope = json.readValue(message, EventEnvelope.class);
        RailRequest request = json.treeToValue(envelope.payload(), RailRequest.class);
        gateway.handle(envelope.eventId(), request.transferId(), request.destinationRef(), request.amountMinor(),
            request.currency());
    }

    record RailRequest(UUID transferId, String destinationRef, long amountMinor, String currency) {
    }
}
