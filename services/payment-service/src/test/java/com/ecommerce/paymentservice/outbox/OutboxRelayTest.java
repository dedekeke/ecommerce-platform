package com.ecommerce.paymentservice.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    @SuppressWarnings("rawtypes")
    private KafkaTemplate kafkaTemplate;

    private OutboxRelay relay;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        relay = new OutboxRelay(outboxRepository, kafkaTemplate, 100);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_publishAndMarkAllEvents_when_batchSucceeds() {
        List<OutboxEvent> batch = List.of(
            buildEvent(1L, "order-1", "payment.completed", "PAYMENT_COMPLETED"),
            buildEvent(2L, "order-2", "payment.failed", "PAYMENT_FAILED")
        );
        when(outboxRepository.findUnpublished(any(Pageable.class))).thenReturn(batch);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
            .thenAnswer(inv -> succeededFuture((ProducerRecord<String, String>) inv.getArgument(0)));
        when(outboxRepository.markPublished(anyList(), any(LocalDateTime.class))).thenReturn(2);

        relay.relay();

        ArgumentCaptor<ProducerRecord<String, String>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(2)).send(recordCaptor.capture());
        assertThat(recordCaptor.getAllValues()).extracting(ProducerRecord::topic)
            .containsExactly("payment.completed", "payment.failed");
        assertThat(recordCaptor.getAllValues()).extracting(ProducerRecord::key)
            .containsExactly("order-1", "order-2");

        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxRepository).markPublished(idsCaptor.capture(), any(LocalDateTime.class));
        assertThat(idsCaptor.getValue()).containsExactly(1L, 2L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_skipMarkingFailedRow_when_kafkaSendThrows() {
        OutboxEvent ok = buildEvent(1L, "order-ok", "payment.completed", "PAYMENT_COMPLETED");
        OutboxEvent failing = buildEvent(2L, "order-fail", "payment.completed", "PAYMENT_COMPLETED");

        when(outboxRepository.findUnpublished(any(Pageable.class)))
            .thenReturn(List.of(ok, failing));

        when(kafkaTemplate.send(any(ProducerRecord.class)))
            .thenAnswer(invocation -> {
                ProducerRecord<String, String> rec = invocation.getArgument(0);
                if ("order-fail".equals(rec.key())) {
                    return failedFuture(new RuntimeException("broker unreachable"));
                }
                return succeededFuture(rec);
            });
        when(outboxRepository.markPublished(anyList(), any(LocalDateTime.class))).thenReturn(1);

        relay.relay();

        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxRepository).markPublished(idsCaptor.capture(), any(LocalDateTime.class));
        assertThat(idsCaptor.getValue()).containsExactly(1L);

        ArgumentCaptor<OutboxEvent> savedCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getAttemptCount()).isEqualTo(1);
        assertThat(savedCaptor.getValue().getLastError()).contains("broker unreachable");
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_doNothing_when_noUnpublishedEvents() {
        when(outboxRepository.findUnpublished(any(Pageable.class))).thenReturn(List.of());

        relay.relay();

        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
        verify(outboxRepository, never()).markPublished(anyList(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_setEventIdAndTypeHeaders_when_publishingRecord() {
        OutboxEvent event = buildEvent(1L, "order-1", "payment.completed", "PAYMENT_COMPLETED");
        when(outboxRepository.findUnpublished(any(Pageable.class))).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
            .thenAnswer(inv -> succeededFuture((ProducerRecord<String, String>) inv.getArgument(0)));

        relay.relay();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue();

        assertThat(new String(record.headers().lastHeader("outbox-event-id").value(),
            StandardCharsets.UTF_8))
            .isEqualTo(event.getEventId());
        assertThat(new String(record.headers().lastHeader("outbox-event-type").value(),
            StandardCharsets.UTF_8))
            .isEqualTo("PAYMENT_COMPLETED");
    }

    private OutboxEvent buildEvent(Long id, String aggregateId, String topic, String eventType) {
        return OutboxEvent.builder()
            .id(id)
            .eventId(UUID.randomUUID().toString())
            .aggregateType("Payment")
            .aggregateId(aggregateId)
            .eventType(eventType)
            .topic(topic)
            .payload("{\"orderId\":\"" + aggregateId + "\"}")
            .createdAt(LocalDateTime.now())
            .attemptCount(0)
            .build();
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
