package com.ecommerce.common.event.publisher;

import brave.Span;
import brave.Tracer;
import com.ecommerce.common.event.BaseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Central event publisher for all domain events.
 * Handles event serialization, Kafka publishing, tracing, and error handling.
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    public EventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            Tracer tracer) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
    }

    /**
     * Publish an event to Kafka asynchronously.
     *
     * @param topic The Kafka topic
     * @param event The event to publish
     * @return CompletableFuture with send result
     */
    public CompletableFuture<SendResult<String, String>> publishEvent(String topic, BaseEvent event) {
        Span span = tracer.nextSpan()
                .name("kafka.publish")
                .tag("messaging.system", "kafka")
                .tag("messaging.destination", topic)
                .tag("messaging.operation", "publish")
                .tag("event.type", event.getEventType())
                .tag("event.id", event.getEventId())
                .start();

        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            // Set correlation ID from MDC if available
            String correlationId = MDC.get("correlationId");
            if (correlationId != null && event.getCorrelationId() == null) {
                event.setCorrelationId(correlationId);
            }

            // Serialize event
            String eventJson = serializeEvent(event);
            span.tag("event.size", String.valueOf(eventJson.length()));

            // Get partition key
            String partitionKey = event.getPartitionKey();

            log.info("Publishing event: type={}, id={}, topic={}, partitionKey={}",
                    event.getEventType(), event.getEventId(), topic, partitionKey);

            // Send to Kafka
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, partitionKey, eventJson);

            // Handle success/failure
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    handleSuccess(span, result, event);
                } else {
                    handleFailure(span, ex, event, topic);
                }
                span.finish();
            });

            return future;

        } catch (Exception e) {
            span.tag("error", "true");
            span.tag("error.message", e.getMessage());
            log.error("Failed to publish event: type={}, id={}, topic={}",
                    event.getEventType(), event.getEventId(), topic, e);
            span.finish();
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Publish an event synchronously (blocking).
     * Use this only when you need guaranteed delivery before proceeding.
     *
     * @param topic The Kafka topic
     * @param event The event to publish
     * @return SendResult
     * @throws Exception if publishing fails
     */
    public SendResult<String, String> publishEventSync(String topic, BaseEvent event) throws Exception {
        return publishEvent(topic, event).get();
    }

    /**
     * Serialize event to JSON.
     */
    private String serializeEvent(BaseEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: {}", event, e);
            throw new EventSerializationException("Failed to serialize event", e);
        }
    }

    /**
     * Handle successful publishing.
     */
    private void handleSuccess(Span span, SendResult<String, String> result, BaseEvent event) {
        span.tag("status", "success");
        span.tag("kafka.partition", String.valueOf(result.getRecordMetadata().partition()));
        span.tag("kafka.offset", String.valueOf(result.getRecordMetadata().offset()));

        log.info("Event published successfully: type={}, id={}, partition={}, offset={}",
                event.getEventType(),
                event.getEventId(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
    }

    /**
     * Handle publishing failure.
     */
    private void handleFailure(Span span, Throwable ex, BaseEvent event, String topic) {
        span.tag("status", "error");
        span.tag("error", "true");
        span.tag("error.message", ex.getMessage());

        log.error("Failed to publish event: type={}, id={}, topic={}",
                event.getEventType(), event.getEventId(), topic, ex);
    }

    /**
     * Exception thrown when event serialization fails.
     */
    public static class EventSerializationException extends RuntimeException {
        public EventSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
