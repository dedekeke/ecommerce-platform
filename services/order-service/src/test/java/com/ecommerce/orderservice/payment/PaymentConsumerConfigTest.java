package com.ecommerce.orderservice.payment;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.util.ReflectionTestUtils;

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
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Captor
    private ArgumentCaptor<ProducerRecord<Object, Object>> producerRecordCaptor;

    private PaymentConsumerConfig config;

    @BeforeEach
    void setUp() {
        config = new PaymentConsumerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(config, "groupId", "order-service-group");
        ReflectionTestUtils.setField(config, "autoStartup", false);
    }

    @Test
    void should_publishToDeadLetterTopic_when_recovererRecoversRecord() {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
        DeadLetterPublishingRecoverer recoverer = config.deadLetterRecoverer(kafkaTemplate);
        ConsumerRecord<String, String> failed =
            new ConsumerRecord<>("payment.completed", 0, 0L, "order-1", "bad-payload");

        recoverer.accept(failed, new PaymentEventProcessingException("poison"));

        verify(kafkaTemplate).send(producerRecordCaptor.capture());
        assertThat(producerRecordCaptor.getValue().topic()).isEqualTo("payment.completed.DLT");
    }

    @Test
    void should_classifyPayloadExceptionAsNotRetryable_so_itIsDeadLetteredImmediately() {
        DefaultErrorHandler handler =
            config.paymentEventErrorHandler(config.deadLetterRecoverer(kafkaTemplate));

        // removeClassification returns the configured retryability: FALSE == not retryable.
        assertThat(handler.removeClassification(PaymentEventProcessingException.class))
            .isEqualTo(Boolean.FALSE);
    }

    @Test
    void should_wireDefaultErrorHandlerOntoContainer_when_factoryBuilt() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            config.paymentEventListenerContainerFactory(kafkaTemplate);

        CommonErrorHandler wired = factory.createContainer("payment.completed").getCommonErrorHandler();

        assertThat(wired).isInstanceOf(DefaultErrorHandler.class);
    }
}
