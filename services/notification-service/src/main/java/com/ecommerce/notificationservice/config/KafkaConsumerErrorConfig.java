package com.ecommerce.notificationservice.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Consumer-side error handling for notification-service Kafka listeners.
 *
 * <p>notification-service previously had no {@code CommonErrorHandler} bean, so
 * a listener that threw would fall back to Spring's default (a few immediate
 * retries then log-and-drop) with no dead-letter topic — the same gap PR#119
 * found and fixed in promotion-service. This wires the standard recovery path.
 *
 * <p>Spring Boot's auto-configured listener container factory picks up a single
 * {@code CommonErrorHandler} bean, so declaring one here applies it to every
 * {@code @KafkaListener} in the service without redefining the factory.
 *
 * <p>Behavior: transient failures (e.g. Mongo unreachable while claiming a dedup
 * key) are retried with exponential backoff; once retries are exhausted the
 * record is published to {@code <topic>.DLT} so a poison message never blocks
 * the partition forever. Benign duplicates never reach this handler — the
 * consumers treat a {@code DuplicateKeyException} as an idempotent success and
 * return normally.
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

        ExponentialBackOff backOff = new ExponentialBackOff(INITIAL_BACKOFF_MS, BACKOFF_MULTIPLIER);
        backOff.setMaxInterval(MAX_BACKOFF_MS);
        backOff.setMaxElapsedTime(MAX_ELAPSED_MS);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.setCommitRecovered(true);
        return handler;
    }
}
