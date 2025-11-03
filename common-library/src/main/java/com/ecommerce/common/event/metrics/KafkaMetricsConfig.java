package com.ecommerce.common.event.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.core.ConsumerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for Kafka metrics exposed to Prometheus.
 * Monitors producer and consumer performance metrics.
 */
@Configuration
@ConditionalOnProperty(value = "management.metrics.export.prometheus.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaMetricsConfig {

    private final MeterRegistry meterRegistry;
    private final ProducerFactory<String, String> producerFactory;
    private final ConsumerFactory<String, String> consumerFactory;
    private final List<KafkaClientMetrics> kafkaMetrics = new ArrayList<>();

    public KafkaMetricsConfig(
            MeterRegistry meterRegistry,
            ProducerFactory<String, String> producerFactory,
            ConsumerFactory<String, String> consumerFactory) {
        this.meterRegistry = meterRegistry;
        this.producerFactory = producerFactory;
        this.consumerFactory = consumerFactory;
    }

    /**
     * Initialize Kafka metrics for monitoring.
     * Exposes metrics like:
     * - kafka.producer.record.send.total
     * - kafka.producer.record.error.total
     * - kafka.consumer.fetch.manager.records.consumed.total
     * - kafka.consumer.fetch.manager.records.lag
     */
    @PostConstruct
    public void initMetrics() {
        // Register producer metrics
        Producer<String, String> producer = producerFactory.createProducer();
        KafkaClientMetrics producerMetrics = new KafkaClientMetrics(producer);
        producerMetrics.bindTo(meterRegistry);
        kafkaMetrics.add(producerMetrics);

        // Register consumer metrics
        Consumer<String, String> consumer = consumerFactory.createConsumer();
        KafkaClientMetrics consumerMetrics = new KafkaClientMetrics(consumer);
        consumerMetrics.bindTo(meterRegistry);
        kafkaMetrics.add(consumerMetrics);
    }

    /**
     * Cleanup metrics on shutdown.
     */
    @PreDestroy
    public void cleanup() {
        kafkaMetrics.forEach(KafkaClientMetrics::close);
    }
}
