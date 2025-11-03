package com.ecommerce.common.event.util;

import com.ecommerce.common.event.BaseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Utility class for event serialization and deserialization.
 * Provides type-safe methods for converting events to/from JSON.
 */
@Component
public class EventSerializationUtil {

    private static final Logger log = LoggerFactory.getLogger(EventSerializationUtil.class);

    private final ObjectMapper objectMapper;

    public EventSerializationUtil(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serialize an event to JSON string.
     *
     * @param event The event to serialize
     * @return JSON string representation
     * @throws EventSerializationException if serialization fails
     */
    public String serializeEvent(BaseEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: type={}, id={}",
                    event.getEventType(), event.getEventId(), e);
            throw new EventSerializationException("Failed to serialize event", e);
        }
    }

    /**
     * Serialize an event to pretty-printed JSON string (for debugging).
     *
     * @param event The event to serialize
     * @return Pretty-printed JSON string
     * @throws EventSerializationException if serialization fails
     */
    public String serializeEventPretty(BaseEvent event) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: type={}, id={}",
                    event.getEventType(), event.getEventId(), e);
            throw new EventSerializationException("Failed to serialize event", e);
        }
    }

    /**
     * Deserialize JSON string to event object.
     *
     * @param json The JSON string
     * @param eventClass The event class type
     * @param <T> Event type
     * @return Deserialized event
     * @throws EventDeserializationException if deserialization fails
     */
    public <T extends BaseEvent> T deserializeEvent(String json, Class<T> eventClass) {
        try {
            return objectMapper.readValue(json, eventClass);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize event to class: {}", eventClass.getName(), e);
            throw new EventDeserializationException("Failed to deserialize event", e);
        }
    }

    /**
     * Deserialize JSON string to event object using TypeReference.
     * Useful for generic types.
     *
     * @param json The JSON string
     * @param typeReference The type reference
     * @param <T> Event type
     * @return Deserialized event
     * @throws EventDeserializationException if deserialization fails
     */
    public <T extends BaseEvent> T deserializeEvent(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize event", e);
            throw new EventDeserializationException("Failed to deserialize event", e);
        }
    }

    /**
     * Deserialize JSON string to BaseEvent (polymorphic).
     * The actual event type will be determined by the eventType field.
     *
     * @param json The JSON string
     * @return Deserialized base event
     * @throws EventDeserializationException if deserialization fails
     */
    public BaseEvent deserializeBaseEvent(String json) {
        try {
            return objectMapper.readValue(json, BaseEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize base event", e);
            throw new EventDeserializationException("Failed to deserialize base event", e);
        }
    }

    /**
     * Validate if a JSON string can be deserialized to an event.
     *
     * @param json The JSON string
     * @param eventClass The event class
     * @return true if valid, false otherwise
     */
    public boolean isValidEvent(String json, Class<? extends BaseEvent> eventClass) {
        try {
            objectMapper.readValue(json, eventClass);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    /**
     * Convert an event to a different event type (useful for event evolution).
     *
     * @param sourceEvent The source event
     * @param targetClass The target event class
     * @param <S> Source event type
     * @param <T> Target event type
     * @return Converted event
     * @throws EventSerializationException if conversion fails
     */
    public <S extends BaseEvent, T extends BaseEvent> T convertEvent(S sourceEvent, Class<T> targetClass) {
        try {
            String json = objectMapper.writeValueAsString(sourceEvent);
            return objectMapper.readValue(json, targetClass);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert event from {} to {}",
                    sourceEvent.getClass().getName(), targetClass.getName(), e);
            throw new EventSerializationException("Failed to convert event", e);
        }
    }

    /**
     * Exception thrown when event serialization fails.
     */
    public static class EventSerializationException extends RuntimeException {
        public EventSerializationException(String message, Throwable cause) {
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
