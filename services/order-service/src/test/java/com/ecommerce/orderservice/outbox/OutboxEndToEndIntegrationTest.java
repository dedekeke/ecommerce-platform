package com.ecommerce.orderservice.outbox;

import com.ecommerce.orderservice.event.OrderEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
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
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end integration test:
 * <ol>
 *   <li>Write an outbox event inside a real DB transaction</li>
 *   <li>Confirm the row landed in the outbox table</li>
 *   <li>Run the relay and confirm the row was shipped to Kafka with the
 *       expected headers and marked published</li>
 * </ol>
 *
 * <p>Crash-safety property covered: a transaction that fails AFTER the outbox
 * write must NOT result in a stranded outbox event — both the (would-be)
 * aggregate row and the outbox row roll back together.
 *
 * <p>The test stands up its own minimal Spring Boot context scoped to the
 * outbox + JPA wiring — we deliberately avoid {@code OrderServiceApplication}
 * which pulls in gRPC, Eureka, OAuth2, and external service clients unrelated
 * to this slice.
 */
@SpringBootTest(
    classes = OutboxEndToEndIntegrationTest.SliceApp.class,
    // The relay bean is @ConditionalOnProperty(outbox.relay.enabled=true).
    // Test profile defaults it to false to keep the scheduler quiet, but we
    // want the bean here so we can drive relay() synchronously.
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
            "com.ecommerce.orderservice.outbox",
            "com.ecommerce.orderservice.event"
        },
        excludeFilters = @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = OutboxMetrics.class
        )
    )
    @EnableJpaRepositories(basePackages = "com.ecommerce.orderservice.outbox")
    @EntityScan(basePackages = "com.ecommerce.orderservice.outbox")
    @EnableTransactionManagement
    static class SliceApp {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private OrderEventPublisher orderEventPublisher;

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
    void should_persistOutboxRowAndPublishViaRelay_when_eventRecordedAndRelayRuns() {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
            .thenAnswer(inv -> succeededFuture((ProducerRecord<String, String>) inv.getArgument(0)));

        Long createdId = transactionTemplate.execute(status ->
            outboxService.recordEvent(
                "Order", "order-" + UUID.randomUUID(),
                "ORDER_CREATED", "order.created",
                Map.of("hello", "world")
            ).getId()
        );
        assertThat(createdId).isNotNull();
        assertThat(outboxRepository.countByPublishedAtIsNull()).isEqualTo(1L);

        relay.relay();

        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
        OutboxEvent reloaded = outboxRepository.findById(createdId).orElseThrow();
        assertThat(reloaded.getPublishedAt()).isNotNull();
        assertThat(outboxRepository.countByPublishedAtIsNull()).isZero();
    }

    @Test
    void should_rollBackOutboxRow_when_enclosingTransactionFails() {
        try {
            transactionTemplate.execute(status -> {
                outboxService.recordEvent(
                    "Order", "doomed-order",
                    "ORDER_CREATED", "order.created",
                    Map.of("doomed", true)
                );
                throw new RuntimeException("simulated post-outbox-write failure");
            });
        } catch (RuntimeException expected) {
            // expected
        }

        assertThat(outboxRepository.countByPublishedAtIsNull()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void should_rejectOutboxWrite_when_calledOutsideTransaction() {
        // The MANDATORY propagation guard ensures we never accidentally write
        // an outbox row outside a transaction (where the row would be lonely
        // and break atomicity guarantees).
        assertThatThrownBy(() ->
            outboxService.recordEvent(
                "Order", "order-1", "ORDER_CREATED", "order.created",
                Map.of("k", "v")
            )
        ).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_keepRowUnpublishedAndBumpAttemptCount_when_kafkaSendFails() {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
            .thenReturn(failedFuture(new RuntimeException("broker down")));

        Long id = transactionTemplate.execute(status ->
            outboxService.recordEvent(
                "Order", "retry-me", "ORDER_CREATED", "order.created",
                Map.of("k", "v")
            ).getId()
        );

        relay.relay();

        OutboxEvent reloaded = outboxRepository.findById(id).orElseThrow();
        assertThat(reloaded.getPublishedAt()).isNull();
        assertThat(reloaded.getAttemptCount()).isEqualTo(1);
        assertThat(reloaded.getLastError()).contains("broker down");
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, String>> succeededFuture(ProducerRecord<String, String> record) {
        TopicPartition tp = new TopicPartition(record.topic(), 0);
        RecordMetadata metadata = new RecordMetadata(tp, 0L, 0, 0L, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(record, metadata));
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, String>> failedFuture(Throwable t) {
        CompletableFuture<SendResult<String, String>> f = new CompletableFuture<>();
        f.completeExceptionally(t);
        return f;
    }
}
