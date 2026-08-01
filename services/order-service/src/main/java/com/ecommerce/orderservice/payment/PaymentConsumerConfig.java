package com.ecommerce.orderservice.payment;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Dedicated listener container factory + error handling for the payment-event
 * consumer.
 *
 * <p>The service-wide default consumer deserializer is JSON, but the payment
 * outbox relay ships the payload as a raw JSON string, so this factory uses
 * {@link StringDeserializer} for key and value ({@link PaymentEventConsumer}
 * parses the body itself, matching notification-service). It is isolated from
 * the global config so nothing else is affected.
 *
 * <p>Because this is a bespoke factory, a stray {@code CommonErrorHandler} bean
 * would NOT be auto-applied to it — so we wire one explicitly, mirroring the
 * repo convention (promotion-service {@code KafkaConsumerErrorConfig}, PR#119):
 * transient failures are retried with bounded exponential backoff, then the
 * record is published to {@code <topic>.DLT}; poison payloads are classified
 * non-retryable and go straight to the DLT. This matters on a money-settlement
 * topic — a dropped payment event would leave an order stuck PENDING forever,
 * so nothing is silently swallowed; it is at least recoverable/alertable on the
 * DLT. Benign dedup skips never reach this handler ({@link PaymentEventHandler}
 * returns normally on a duplicate, so the record is acked, not dead-lettered).
 *
 * <p>{@code auto-startup} is a property (default true) so the {@code test}
 * profile can disable container startup — the H2 tests drive the handler and
 * consumer directly and need no live broker.
 */
@Configuration
public class PaymentConsumerConfig {

    private static final long INITIAL_BACKOFF_MS = 1_000L;
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final long MAX_BACKOFF_MS = 10_000L;
    private static final long MAX_ELAPSED_MS = 30_000L;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:order-service-group}")
    private String groupId;

    @Value("${payment.consumer.auto-startup:true}")
    private boolean autoStartup;

    @Bean
    public ConsumerFactory<String, String> paymentConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> paymentEventListenerContainerFactory(
        ProducerFactory<Object, Object> producerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentConsumerFactory());
        factory.setCommonErrorHandler(
            paymentEventErrorHandler(deadLetterRecoverer(dltKafkaTemplate(producerFactory))));
        factory.setAutoStartup(autoStartup);
        return factory;
    }

    /**
     * Dedicated {@code String}→{@code String} template for the dead-letter
     * recoverer. The app-wide default template value-serializes with
     * {@code JsonSerializer}, which would JSON-quote/escape the raw String
     * payment payload and stamp a spurious {@code __TypeId__} header — so the
     * dead-lettered record would NOT be byte-identical to the original. We copy
     * the tuned default producer config (acks=all, idempotence, compression) and
     * override only the serializers to {@link StringSerializer}, mirroring the
     * outbox relay's String template. Not a {@code @Bean}: exposing another
     * {@code KafkaTemplate}/{@code ProducerFactory} bean would trip Boot's
     * {@code @ConditionalOnMissingBean} and disable the auto-configured default.
     */
    KafkaTemplate<String, String> dltKafkaTemplate(ProducerFactory<Object, Object> defaultProducerFactory) {
        Map<String, Object> serializerOverride = Map.of(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        @SuppressWarnings("unchecked")
        ProducerFactory<String, String> stringProducerFactory =
            (ProducerFactory<String, String>) (ProducerFactory<?, ?>)
                defaultProducerFactory.copyWithConfigurationOverride(serializerOverride);
        return new KafkaTemplate<>(stringProducerFactory);
    }

    /**
     * Publishes exhausted/poison records to {@code <topic>.DLT}. Partition -1
     * lets Kafka choose, so it works regardless of the DLT topic's partitioning.
     */
    DeadLetterPublishingRecoverer deadLetterRecoverer(KafkaTemplate<String, String> dltTemplate) {
        return new DeadLetterPublishingRecoverer(
            dltTemplate,
            (record, exception) -> new TopicPartition(record.topic() + ".DLT", -1));
    }

    /**
     * Retries transient failures with exponential backoff (1s, 2s, 4s, ... capped
     * at 10s/attempt, 30s total), then recovers to the DLT. A
     * {@link PaymentEventProcessingException} (bad/incomplete payload) is
     * non-retryable and is dead-lettered immediately.
     */
    DefaultErrorHandler paymentEventErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        ExponentialBackOff backOff = new ExponentialBackOff(INITIAL_BACKOFF_MS, BACKOFF_MULTIPLIER);
        backOff.setMaxInterval(MAX_BACKOFF_MS);
        backOff.setMaxElapsedTime(MAX_ELAPSED_MS);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.setCommitRecovered(true);
        handler.addNotRetryableExceptions(PaymentEventProcessingException.class);
        return handler;
    }
}
