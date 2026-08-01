package com.ecommerce.orderservice.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application-side write API for the transactional outbox.
 *
 * <p>Callers invoke {@link #recordEvent} from inside their own
 * {@code @Transactional} method that mutates the aggregate (Order, etc.). The
 * outbox row therefore commits atomically with the aggregate row — no
 * dual-write window can drop or duplicate the event. The downstream {@link
 * OutboxRelay} ships the row to Kafka asynchronously.
 *
 * <p>Method is annotated {@code Propagation.MANDATORY}: callers MUST already
 * be inside a transaction. Calling this method outside a transaction defeats
 * the entire pattern and is flagged early as a runtime error rather than
 * silently writing a stranded row.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Persist an outbox row inside the caller's transaction.
     *
     * @param aggregateType domain aggregate label (e.g. {@code "Order"})
     * @param aggregateId   aggregate primary key for partition routing
     * @param eventType     business event name (e.g. {@code "ORDER_CREATED"})
     * @param topic         destination Kafka topic
     * @param payload       event payload — serialised to JSON via Jackson
     * @return the persisted outbox row (id and eventId populated)
     */
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
            // Serialisation failure aborts the surrounding transaction — that
            // is the correct behaviour: we will not commit an aggregate change
            // whose event we cannot record.
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
