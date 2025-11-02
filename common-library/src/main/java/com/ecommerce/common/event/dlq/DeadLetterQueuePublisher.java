package com.ecommerce.common.event.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Publisher for Dead Letter Queue (DLQ).
 * Sends failed messages to DLQ with error metadata for later analysis and retry.
 */
@Component
public class DeadLetterQueuePublisher {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterQueuePublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public DeadLetterQueuePublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publish a failed message to the Dead Letter Queue.
     *
     * @param record The original consumer record
     * @param exception The exception that caused the failure
     * @param dlqTopic The DLQ topic name
     */
    public void publishToDeadLetterQueue(
            ConsumerRecord<String, String> record,
            Exception exception,
            String dlqTopic) {

        try {
            DeadLetterMessage dlqMessage = createDeadLetterMessage(record, exception);
            String messageJson = objectMapper.writeValueAsString(dlqMessage);

            kafkaTemplate.send(dlqTopic, record.key(), messageJson)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Message sent to DLQ successfully: topic={}, partition={}, offset={}",
                                    dlqTopic,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        } else {
                            log.error("Failed to send message to DLQ: topic={}", dlqTopic, ex);
                        }
                    });

        } catch (Exception e) {
            log.error("Failed to serialize DLQ message for topic: {}", dlqTopic, e);
        }
    }

    /**
     * Create a Dead Letter Message with metadata.
     */
    private DeadLetterMessage createDeadLetterMessage(
            ConsumerRecord<String, String> record,
            Exception exception) {

        DeadLetterMessage message = new DeadLetterMessage();
        message.setOriginalTopic(record.topic());
        message.setOriginalPartition(record.partition());
        message.setOriginalOffset(record.offset());
        message.setOriginalKey(record.key());
        message.setOriginalValue(record.value());
        message.setOriginalTimestamp(record.timestamp());
        message.setFailedAt(Instant.now());
        message.setExceptionClass(exception.getClass().getName());
        message.setExceptionMessage(exception.getMessage());
        message.setStackTrace(getStackTraceAsString(exception));

        // Extract headers
        Map<String, String> headers = new HashMap<>();
        record.headers().forEach(header ->
                headers.put(header.key(), new String(header.value()))
        );
        message.setHeaders(headers);

        return message;
    }

    /**
     * Convert stack trace to string.
     */
    private String getStackTraceAsString(Exception exception) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : exception.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Dead Letter Message structure.
     */
    public static class DeadLetterMessage {
        private String originalTopic;
        private int originalPartition;
        private long originalOffset;
        private String originalKey;
        private String originalValue;
        private long originalTimestamp;
        private Instant failedAt;
        private String exceptionClass;
        private String exceptionMessage;
        private String stackTrace;
        private Map<String, String> headers;

        // Getters and Setters

        public String getOriginalTopic() {
            return originalTopic;
        }

        public void setOriginalTopic(String originalTopic) {
            this.originalTopic = originalTopic;
        }

        public int getOriginalPartition() {
            return originalPartition;
        }

        public void setOriginalPartition(int originalPartition) {
            this.originalPartition = originalPartition;
        }

        public long getOriginalOffset() {
            return originalOffset;
        }

        public void setOriginalOffset(long originalOffset) {
            this.originalOffset = originalOffset;
        }

        public String getOriginalKey() {
            return originalKey;
        }

        public void setOriginalKey(String originalKey) {
            this.originalKey = originalKey;
        }

        public String getOriginalValue() {
            return originalValue;
        }

        public void setOriginalValue(String originalValue) {
            this.originalValue = originalValue;
        }

        public long getOriginalTimestamp() {
            return originalTimestamp;
        }

        public void setOriginalTimestamp(long originalTimestamp) {
            this.originalTimestamp = originalTimestamp;
        }

        public Instant getFailedAt() {
            return failedAt;
        }

        public void setFailedAt(Instant failedAt) {
            this.failedAt = failedAt;
        }

        public String getExceptionClass() {
            return exceptionClass;
        }

        public void setExceptionClass(String exceptionClass) {
            this.exceptionClass = exceptionClass;
        }

        public String getExceptionMessage() {
            return exceptionMessage;
        }

        public void setExceptionMessage(String exceptionMessage) {
            this.exceptionMessage = exceptionMessage;
        }

        public String getStackTrace() {
            return stackTrace;
        }

        public void setStackTrace(String stackTrace) {
            this.stackTrace = stackTrace;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers;
        }
    }
}
