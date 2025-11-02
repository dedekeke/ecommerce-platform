package com.ecommerce.common.event.consumer;

import brave.Span;
import brave.Tracer;
import com.ecommerce.common.event.BaseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.support.Acknowledgment;

/**
 * Base class for all event consumers.
 * Provides common functionality for event deserialization, tracing, and error handling.
 *
 * @param <T> The event type this consumer handles
 */
public abstract class BaseEventConsumer<T extends BaseEvent> {

    private static final Logger log = LoggerFactory.getLogger(BaseEventConsumer.class);

    protected final ObjectMapper objectMapper;
    protected final Tracer tracer;
    protected final Class<T> eventClass;

    protected BaseEventConsumer(ObjectMapper objectMapper, Tracer tracer, Class<T> eventClass) {
        this.objectMapper = objectMapper;
        this.tracer = tracer;
        this.eventClass = eventClass;
    }

    /**
     * Process an event message.
     * This method handles deserialization, tracing, MDC setup, and acknowledgment.
     *
     * @param message The raw message from Kafka
     * @param acknowledgment Kafka acknowledgment for manual commit
     */
    protected void processEvent(String message, Acknowledgment acknowledgment) {
        Span span = null;

        try {
            // Deserialize event
            T event = deserializeEvent(message);

            // Create span for event processing
            span = createProcessingSpan(event);

            try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
                // Set MDC context
                setupMDC(event);

                log.info("Processing event: type={}, id={}, correlationId={}",
                        event.getEventType(), event.getEventId(), event.getCorrelationId());

                // Process the event (implemented by subclass)
                handleEvent(event);

                // Acknowledge successful processing
                if (acknowledgment != null) {
                    acknowledgment.acknowledge();
                }

                span.tag("status", "success");
                log.info("Event processed successfully: type={}, id={}",
                        event.getEventType(), event.getEventId());

            } catch (Exception e) {
                span.tag("status", "error");
                span.tag("error", "true");
                span.tag("error.message", e.getMessage());

                log.error("Failed to process event: type={}, id={}",
                        event.getEventType(), event.getEventId(), e);

                // Don't acknowledge - let retry logic handle it
                throw e;
            } finally {
                MDC.clear();
            }

        } catch (Exception e) {
            if (span != null) {
                span.tag("status", "error");
                span.tag("error", "true");
                span.tag("error.message", e.getMessage());
            }

            log.error("Failed to deserialize or process event message: {}", message, e);
            throw new EventProcessingException("Failed to process event", e);

        } finally {
            if (span != null) {
                span.finish();
            }
        }
    }

    /**
     * Deserialize JSON message to event object.
     */
    private T deserializeEvent(String message) {
        try {
            return objectMapper.readValue(message, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize event: {}", message, e);
            throw new EventDeserializationException("Failed to deserialize event", e);
        }
    }

    /**
     * Create a span for event processing.
     */
    private Span createProcessingSpan(T event) {
        return tracer.nextSpan()
                .name("kafka.consume")
                .tag("messaging.system", "kafka")
                .tag("messaging.operation", "consume")
                .tag("event.type", event.getEventType())
                .tag("event.id", event.getEventId())
                .tag("event.source", event.getSource())
                .start();
    }

    /**
     * Set up MDC context with event metadata.
     */
    private void setupMDC(T event) {
        if (event.getCorrelationId() != null) {
            MDC.put("correlationId", event.getCorrelationId());
        }
        if (event.getUserId() != null) {
            MDC.put("userId", event.getUserId());
        }
        MDC.put("eventId", event.getEventId());
        MDC.put("eventType", event.getEventType());
    }

    /**
     * Handle the event (implemented by subclass).
     * This method should contain the business logic for processing the event.
     *
     * @param event The deserialized event
     * @throws Exception if processing fails
     */
    protected abstract void handleEvent(T event) throws Exception;

    /**
     * Get the event class this consumer handles.
     */
    protected Class<T> getEventClass() {
        return eventClass;
    }

    /**
     * Exception thrown when event processing fails.
     */
    public static class EventProcessingException extends RuntimeException {
        public EventProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when event deserialization fails.
     */
    public static class EventDeserializationException extends RuntimeException {
        public EventDeserializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
