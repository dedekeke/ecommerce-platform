package com.ecommerce.orderservice.payment;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the payment consumer's error handling is actually wired (the HIGH gap
 * from PR#130 review): poison/exhausted records are routed to {@code
 * <topic>.DLT} instead of being silently dropped, and a payload exception is
 * classified non-retryable so it is dead-lettered immediately.
 */
@ExtendWith(MockitoExtension.class)
class PaymentConsumerConfigTest {

    @Mock
    private KafkaTemplate<String, String> dltTemplate;

    @Captor
    private ArgumentCaptor<ProducerRecord<String, String>> producerRecordCaptor;

    private PaymentConsumerConfig config;

    @BeforeEach
    void setUp() {
        config = new PaymentConsumerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(config, "groupId", "order-service-group");
        ReflectionTestUtils.setField(config, "autoStartup", false);
    }

    /**
     * A default-like producer factory: String key + JsonSerializer value, exactly
     * like the app-wide {@code spring.kafka.producer} config the recoverer would
     * otherwise (wrongly) reuse.
     */
    private static ProducerFactory<Object, Object> jsonProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Test
    void should_publishToDeadLetterTopic_when_recovererRecoversRecord() {
        when(dltTemplate.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
        DeadLetterPublishingRecoverer recoverer = config.deadLetterRecoverer(dltTemplate);
        ConsumerRecord<String, String> failed =
            new ConsumerRecord<>("payment.completed", 0, 0L, "order-1", "bad-payload");

        recoverer.accept(failed, new PaymentEventProcessingException("poison"));

        verify(dltTemplate).send(producerRecordCaptor.capture());
        assertThat(producerRecordCaptor.getValue().topic()).isEqualTo("payment.completed.DLT");
    }

    @Test
    void should_classifyPayloadExceptionAsNotRetryable_so_itIsDeadLetteredImmediately() {
        DefaultErrorHandler handler =
            config.paymentEventErrorHandler(config.deadLetterRecoverer(dltTemplate));

        // removeClassification returns the configured retryability: FALSE == not retryable.
        assertThat(handler.removeClassification(PaymentEventProcessingException.class))
            .isEqualTo(Boolean.FALSE);
    }

    @Test
    void should_wireDefaultErrorHandlerOntoContainer_when_factoryBuilt() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            config.paymentEventListenerContainerFactory(jsonProducerFactory());

        CommonErrorHandler wired = factory.createContainer("payment.completed").getCommonErrorHandler();

        assertThat(wired).isInstanceOf(DefaultErrorHandler.class);
    }

    @Test
    void should_overrideValueSerializerToString_so_dltRecordIsNotJsonQuoted() {
        // The default template would JSON-quote the String payload; the DLT
        // template must value-serialize as a raw String (byte-identical, no
        // stray __TypeId__ header).
        KafkaTemplate<String, String> template = config.dltKafkaTemplate(jsonProducerFactory());

        assertThat(template.getProducerFactory().getConfigurationProperties())
            .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    }

    @Test
    void should_overrideKeySerializerToString_forDltTemplate() {
        KafkaTemplate<String, String> template = config.dltKafkaTemplate(jsonProducerFactory());

        assertThat(template.getProducerFactory().getConfigurationProperties())
            .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    }
}
