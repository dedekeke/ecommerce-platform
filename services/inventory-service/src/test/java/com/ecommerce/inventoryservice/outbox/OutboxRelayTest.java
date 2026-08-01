package com.ecommerce.inventoryservice.outbox;

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
import java.util.ArrayList;
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

/**
 * Unit tests for {@link OutboxRelay}. The relay's contract:
 * <ul>
 *   <li>Claim batch via {@code findUnpublished}</li>
 *   <li>Send each row to Kafka with the {@code outbox-event-id} header</li>
 *   <li>Bulk-mark successfully published rows</li>
 *   <li>Leave failed rows unpublished and bump {@code attemptCount}</li>
 * </ul>
 */
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
            buildEvent(1L, "100", "stock.low.detected", "STOCK_LOW_DETECTED"),
            buildEvent(2L, "200", "stock.low.detected", "STOCK_LOW_DETECTED"),
            buildEvent(3L, "300", "stock.replenished", "STOCK_REPLENISHED")
        );
        when(outboxRepository.findUnpublished(any(Pageable.class))).thenReturn(batch);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
            .thenAnswer(inv -> succeededFuture((ProducerRecord<String, String>) inv.getArgument(0)));
        when(outboxRepository.markPublished(anyList(), any(LocalDateTime.class))).thenReturn(3);

        relay.relay();

        ArgumentCaptor<ProducerRecord<String, String>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(3)).send(recordCaptor.capture());

        List<ProducerRecord<String, String>> sent = recordCaptor.getAllValues();
        assertThat(sent).extracting(ProducerRecord::topic)
            .containsExactly("stock.low.detected", "stock.low.detected", "stock.replenished");
        assertThat(sent).extracting(ProducerRecord::key)
            .containsExactly("100", "200", "300");
        // Each record must carry the outbox-event-id header for consumer dedup.
        assertThat(sent).allSatisfy(r ->
            assertThat(r.headers().lastHeader("outbox-event-id")).isNotNull());

        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxRepository).markPublished(idsCaptor.capture(), any(LocalDateTime.class));
        assertThat(idsCaptor.getValue()).containsExactly(1L, 2L, 3L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_skipMarkingFailedRow_when_kafkaSendThrows() {
        OutboxEvent ok1 = buildEvent(1L, "100", "stock.low.detected", "STOCK_LOW_DETECTED");
        OutboxEvent failing = buildEvent(2L, "fail-me", "stock.low.detected", "STOCK_LOW_DETECTED");
        OutboxEvent ok2 = buildEvent(3L, "300", "stock.low.detected", "STOCK_LOW_DETECTED");

        when(outboxRepository.findUnpublished(any(Pageable.class)))
            .thenReturn(List.of(ok1, failing, ok2));

        when(kafkaTemplate.send(any(ProducerRecord.class)))
            .thenAnswer(invocation -> {
                ProducerRecord<String, String> rec = invocation.getArgument(0);
                if ("fail-me".equals(rec.key())) {
                    return failedFuture(new RuntimeException("broker unreachable"));
                }
                return succeededFuture(rec);
            });

        when(outboxRepository.markPublished(anyList(), any(LocalDateTime.class))).thenReturn(2);

        relay.relay();

        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxRepository).markPublished(idsCaptor.capture(), any(LocalDateTime.class));
        // Failing row must stay unpublished so the next poll can retry it.
        assertThat(idsCaptor.getValue()).containsExactly(1L, 3L);

        // Failed row must have its attempt count bumped and lastError captured.
        ArgumentCaptor<OutboxEvent> savedCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(savedCaptor.capture());
        OutboxEvent updated = savedCaptor.getValue();
        assertThat(updated.getId()).isEqualTo(2L);
        assertThat(updated.getAttemptCount()).isEqualTo(1);
        assertThat(updated.getLastError()).contains("broker unreachable");
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
    void should_notMarkAnythingPublished_when_allSendsFail() {
        OutboxEvent e1 = buildEvent(1L, "100", "stock.low.detected", "STOCK_LOW_DETECTED");
        OutboxEvent e2 = buildEvent(2L, "200", "stock.low.detected", "STOCK_LOW_DETECTED");
        when(outboxRepository.findUnpublished(any(Pageable.class))).thenReturn(List.of(e1, e2));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
            .thenReturn(failedFuture(new RuntimeException("kafka down")));

        relay.relay();

        verify(outboxRepository, never()).markPublished(anyList(), any());
        // Both rows must be saved with bumped attemptCount.
        verify(outboxRepository, times(2)).save(any(OutboxEvent.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_setEventTypeHeader_when_publishingRecord() {
        OutboxEvent event = buildEvent(1L, "100", "stock.low.detected", "STOCK_LOW_DETECTED");
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
            .isEqualTo("STOCK_LOW_DETECTED");
    }

    private OutboxEvent buildEvent(Long id, String aggregateId, String topic, String eventType) {
        return OutboxEvent.builder()
            .id(id)
            .eventId(UUID.randomUUID().toString())
            .aggregateType("Inventory")
            .aggregateId(aggregateId)
            .eventType(eventType)
            .topic(topic)
            .payload("{\"productId\":\"" + aggregateId + "\"}")
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

    @SuppressWarnings("unused")
    private static List<OutboxEvent> defensiveCopy(List<OutboxEvent> src) {
        return new ArrayList<>(src);
    }
}
