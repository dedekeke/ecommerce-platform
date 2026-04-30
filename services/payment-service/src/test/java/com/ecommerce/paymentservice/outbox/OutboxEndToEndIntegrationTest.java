package com.ecommerce.paymentservice.outbox;

import com.ecommerce.paymentservice.kafka.PaymentEvent;
import com.ecommerce.paymentservice.kafka.PaymentEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end test that wires PaymentEventPublisher → OutboxService → real H2
 * → OutboxRelay → mocked KafkaTemplate. Confirms the publisher records the
 * row inside a transaction and the relay subsequently ships it to Kafka.
 */
@SpringBootTest(
    classes = OutboxEndToEndIntegrationTest.SliceApp.class,
    properties = "outbox.relay.enabled=true"
)
@ActiveProfiles("test")
class OutboxEndToEndIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
        SecurityAutoConfiguration.class,
        OAuth2ClientAutoConfiguration.class,
        OAuth2ResourceServerAutoConfiguration.class,
        KafkaAutoConfiguration.class,
        org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration.class
    })
    @ComponentScan(
        basePackages = {
            "com.ecommerce.paymentservice.outbox",
            "com.ecommerce.paymentservice.kafka"
        },
        excludeFilters = @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = OutboxMetrics.class
        )
    )
    @EnableJpaRepositories(basePackages = "com.ecommerce.paymentservice.outbox")
    @EntityScan(basePackages = "com.ecommerce.paymentservice.outbox")
    @EnableTransactionManagement
    static class SliceApp {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private PaymentEventPublisher paymentEventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    @SuppressWarnings("rawtypes")
    private KafkaTemplate kafkaTemplate;

    @BeforeEach
    void cleanup() {
        outboxRepository.deleteAll();
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_recordOutboxRowInsideTransaction_when_publishingPaymentCompleted() {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
            .thenAnswer(inv -> succeededFuture((ProducerRecord<String, String>) inv.getArgument(0)));

        PaymentEvent event = buildEvent("PAYMENT_COMPLETED");

        // Publisher itself doesn't open a tx — its caller (PaymentService.confirmPayment)
        // is @Transactional. Wrap in a TransactionTemplate to mirror that lifecycle.
        transactionTemplate.executeWithoutResult(status ->
            paymentEventPublisher.publishPaymentCompletedEvent(event)
        );

        assertThat(outboxRepository.count()).isEqualTo(1L);
        OutboxEvent persisted = outboxRepository.findAll().get(0);
        assertThat(persisted.getEventType()).isEqualTo("PAYMENT_COMPLETED");
        assertThat(persisted.getTopic()).isEqualTo("payment.completed");
        assertThat(persisted.getAggregateId()).isEqualTo("order-99");
        assertThat(persisted.getPublishedAt()).isNull();

        relay.relay();

        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
        OutboxEvent reloaded = outboxRepository.findById(persisted.getId()).orElseThrow();
        assertThat(reloaded.getPublishedAt()).isNotNull();
    }

    @Test
    void should_rollBackOutboxRow_when_enclosingTransactionFails() {
        PaymentEvent event = buildEvent("PAYMENT_COMPLETED");

        try {
            transactionTemplate.executeWithoutResult(status -> {
                paymentEventPublisher.publishPaymentCompletedEvent(event);
                throw new RuntimeException("simulated downstream failure");
            });
        } catch (RuntimeException expected) {
            // expected
        }

        assertThat(outboxRepository.count()).isZero();
    }

    private PaymentEvent buildEvent(String type) {
        return PaymentEvent.builder()
            .eventType(type)
            .paymentId(1L)
            .orderId("order-99")
            .userId("user-1")
            .paymentIntentId("pi_123")
            .transactionId("txn_abc")
            .amount(new BigDecimal("42.00"))
            .currency("USD")
            .status("COMPLETED")
            .timestamp(LocalDateTime.now())
            .build();
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, String>> succeededFuture(ProducerRecord<String, String> record) {
        TopicPartition tp = new TopicPartition(record.topic(), 0);
        RecordMetadata metadata = new RecordMetadata(tp, 0L, 0, 0L, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(record, metadata));
    }
}
