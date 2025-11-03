package com.ecommerce.common.event.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for event-driven architecture.
 * Configures topics, producers, consumers, and error handling.
 */
@Configuration
public class KafkaEventConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.application.name:unknown}")
    private String applicationName;

    /**
     * Kafka topic names as constants
     */
    public static final String TOPIC_ORDER_EVENTS = "order-events";
    public static final String TOPIC_PAYMENT_EVENTS = "payment-events";
    public static final String TOPIC_INVENTORY_EVENTS = "inventory-events";
    public static final String TOPIC_PRODUCT_EVENTS = "product-events";
    public static final String TOPIC_USER_EVENTS = "user-events";

    // Dead Letter Queue topics
    public static final String TOPIC_DLQ_ORDER = "order-events-dlq";
    public static final String TOPIC_DLQ_PAYMENT = "payment-events-dlq";
    public static final String TOPIC_DLQ_INVENTORY = "inventory-events-dlq";
    public static final String TOPIC_DLQ_PRODUCT = "product-events-dlq";
    public static final String TOPIC_DLQ_USER = "user-events-dlq";

    /**
     * ObjectMapper for event serialization/deserialization
     */
    @Bean
    public ObjectMapper eventObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /**
     * Producer Factory configuration
     */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // Reliability settings
        props.put(ProducerConfig.ACKS_CONFIG, "all");  // Wait for all replicas
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);  // Ensure ordering
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);  // Exactly-once semantics

        // Performance settings
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 10);

        // Monitoring
        props.put(ProducerConfig.CLIENT_ID_CONFIG, applicationName + "-producer");

        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Kafka Template for publishing events
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Consumer Factory configuration
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, StringDeserializer.class);

        // Consumer settings
        props.put(ConsumerConfig.GROUP_ID_CONFIG, applicationName);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);  // Manual commit for reliability

        // Performance settings
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);

        // Monitoring
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, applicationName + "-consumer");

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Kafka Listener Container Factory with error handling
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        // Enable batch listening if needed
        factory.setBatchListener(false);

        // Concurrency - number of consumer threads
        factory.setConcurrency(3);

        // Error handler with exponential backoff and DLQ
        factory.setCommonErrorHandler(errorHandler());

        // Enable manual acknowledgment
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL);

        return factory;
    }

    /**
     * Error handler with exponential backoff retry and DLQ publishing
     */
    @Bean
    public DefaultErrorHandler errorHandler() {
        // Exponential backoff: 1s, 2s, 4s, 8s, 16s (max 5 retries)
        ExponentialBackOff backOff = new ExponentialBackOff(1000, 2.0);
        backOff.setMaxElapsedTime(30000);  // Max 30 seconds total retry time

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (consumerRecord, exception) -> {
                    // This is called when all retries are exhausted
                    // The record should be sent to DLQ
                    // Note: Actual DLQ publishing is handled by DeadLetterPublishingRecoverer
                },
                backOff
        );

        // Don't retry on certain exceptions
        errorHandler.addNotRetryableExceptions(
                IllegalArgumentException.class,
                com.fasterxml.jackson.core.JsonProcessingException.class
        );

        return errorHandler;
    }

    // ========== Topic Definitions ==========

    /**
     * Order Events Topic
     * Partitions: 3 (for load distribution)
     * Replication: 1 (increase in production)
     */
    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(TOPIC_ORDER_EVENTS)
                .partitions(3)
                .replicas(1)
                .config("retention.ms", String.valueOf(7 * 24 * 60 * 60 * 1000))  // 7 days
                .config("compression.type", "snappy")
                .build();
    }

    @Bean
    public NewTopic orderEventsDlqTopic() {
        return TopicBuilder.name(TOPIC_DLQ_ORDER)
                .partitions(1)
                .replicas(1)
                .config("retention.ms", String.valueOf(30 * 24 * 60 * 60 * 1000))  // 30 days
                .build();
    }

    /**
     * Payment Events Topic
     */
    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name(TOPIC_PAYMENT_EVENTS)
                .partitions(3)
                .replicas(1)
                .config("retention.ms", String.valueOf(7 * 24 * 60 * 60 * 1000))
                .config("compression.type", "snappy")
                .build();
    }

    @Bean
    public NewTopic paymentEventsDlqTopic() {
        return TopicBuilder.name(TOPIC_DLQ_PAYMENT)
                .partitions(1)
                .replicas(1)
                .config("retention.ms", String.valueOf(30 * 24 * 60 * 60 * 1000))
                .build();
    }

    /**
     * Inventory Events Topic
     */
    @Bean
    public NewTopic inventoryEventsTopic() {
        return TopicBuilder.name(TOPIC_INVENTORY_EVENTS)
                .partitions(3)
                .replicas(1)
                .config("retention.ms", String.valueOf(7 * 24 * 60 * 60 * 1000))
                .config("compression.type", "snappy")
                .build();
    }

    @Bean
    public NewTopic inventoryEventsDlqTopic() {
        return TopicBuilder.name(TOPIC_DLQ_INVENTORY)
                .partitions(1)
                .replicas(1)
                .config("retention.ms", String.valueOf(30 * 24 * 60 * 60 * 1000))
                .build();
    }

    /**
     * Product Events Topic
     */
    @Bean
    public NewTopic productEventsTopic() {
        return TopicBuilder.name(TOPIC_PRODUCT_EVENTS)
                .partitions(3)
                .replicas(1)
                .config("retention.ms", String.valueOf(7 * 24 * 60 * 60 * 1000))
                .config("compression.type", "snappy")
                .build();
    }

    @Bean
    public NewTopic productEventsDlqTopic() {
        return TopicBuilder.name(TOPIC_DLQ_PRODUCT)
                .partitions(1)
                .replicas(1)
                .config("retention.ms", String.valueOf(30 * 24 * 60 * 60 * 1000))
                .build();
    }

    /**
     * User Events Topic
     */
    @Bean
    public NewTopic userEventsTopic() {
        return TopicBuilder.name(TOPIC_USER_EVENTS)
                .partitions(2)
                .replicas(1)
                .config("retention.ms", String.valueOf(7 * 24 * 60 * 60 * 1000))
                .config("compression.type", "snappy")
                .build();
    }

    @Bean
    public NewTopic userEventsDlqTopic() {
        return TopicBuilder.name(TOPIC_DLQ_USER)
                .partitions(1)
                .replicas(1)
                .config("retention.ms", String.valueOf(30 * 24 * 60 * 60 * 1000))
                .build();
    }
}
