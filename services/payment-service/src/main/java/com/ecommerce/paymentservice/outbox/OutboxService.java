package com.ecommerce.paymentservice.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application-side write API for the transactional outbox in payment-service.
 * See order-service's OutboxService for full semantics.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent recordEvent(
        String aggregateType,
        String aggregateId,
        String eventType,
        String topic,
        Object payload
    ) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new OutboxSerializationException(
                "Failed to serialise outbox payload for " + eventType, e);
        }

        OutboxEvent event = OutboxEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .aggregateType(aggregateType)
            .aggregateId(aggregateId)
            .eventType(eventType)
            .topic(topic)
            .payload(json)
            .attemptCount(0)
            .build();

        OutboxEvent saved = outboxRepository.save(event);
        log.debug("Outbox event recorded: type={} aggregate={}/{} eventId={}",
            eventType, aggregateType, aggregateId, saved.getEventId());
        return saved;
    }

    public static class OutboxSerializationException extends RuntimeException {
        public OutboxSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
