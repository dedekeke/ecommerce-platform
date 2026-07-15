package com.ecommerce.promotionservice.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Consumer-side error handling for promotion-service Kafka listeners.
 *
 * <p>Spring Boot's auto-configured listener container factory picks up a single
 * {@code CommonErrorHandler} bean, so declaring one here wires it for every
 * {@code @KafkaListener} in this service without redefining the factory.
 *
 * <p>Why not reuse {@code common-library}'s {@code KafkaEventConfig}? It is not
 * component-scanned by {@code PromotionServiceApplication} (different base
 * package), its recoverer is a no-op that never actually publishes to a DLQ,
 * and it forces MANUAL ack plus a set of order/payment topic beans that do not
 * belong to this service. This is the minimal, correct wiring instead.
 *
 * <p>Behavior: transient failures are retried with exponential backoff; once
 * retries are exhausted the record is published to {@code <topic>.DLT} so a
 * poison message never blocks the partition forever. (Benign duplicate-key
 * violations never reach this handler — {@code OrderCompletedConsumer} catches
 * them and returns normally.)
 */
@Configuration
@Slf4j
public class KafkaConsumerErrorConfig {

    private static final long INITIAL_BACKOFF_MS = 1_000L;
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final long MAX_BACKOFF_MS = 10_000L;
    private static final long MAX_ELAPSED_MS = 30_000L;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                // Route to "<topic>.DLT"; partition -1 lets Kafka choose so this
                // works regardless of the DLT topic's partition count.
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", -1));

        // Exponential backoff (1s, 2s, 4s, 8s, ...) capped at 10s per attempt and
        // 30s total; once exhausted the record is recovered to the DLT.
        ExponentialBackOff backOff = new ExponentialBackOff(INITIAL_BACKOFF_MS, BACKOFF_MULTIPLIER);
        backOff.setMaxInterval(MAX_BACKOFF_MS);
        backOff.setMaxElapsedTime(MAX_ELAPSED_MS);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.setCommitRecovered(true);
        return handler;
    }
}
