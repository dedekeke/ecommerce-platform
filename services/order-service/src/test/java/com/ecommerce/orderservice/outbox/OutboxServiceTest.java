package com.ecommerce.orderservice.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OutboxService}. We use a real ObjectMapper so we
 * actually exercise serialisation, but mock the repository to assert the
 * row shape that gets persisted.
 */
@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxRepository outboxRepository;

    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        outboxService = new OutboxService(outboxRepository, new ObjectMapper());
    }

    @Test
    void should_persistOutboxRow_when_recordingEvent() {
        when(outboxRepository.save(any(OutboxEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> payload = Map.of("orderId", "order-1", "total", 42);

        OutboxEvent saved = outboxService.recordEvent(
            "Order", "order-1", "ORDER_CREATED", "order.created", payload);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent persisted = captor.getValue();
        assertThat(persisted.getAggregateType()).isEqualTo("Order");
        assertThat(persisted.getAggregateId()).isEqualTo("order-1");
        assertThat(persisted.getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(persisted.getTopic()).isEqualTo("order.created");
        assertThat(persisted.getPayload())
            .contains("\"orderId\":\"order-1\"")
            .contains("\"total\":42");
        assertThat(persisted.getEventId()).isNotBlank();
        assertThat(persisted.getPublishedAt()).isNull();
        assertThat(persisted.getAttemptCount()).isZero();
        assertThat(saved).isSameAs(persisted);
    }

    @Test
    void should_generateUniqueEventId_when_recordingMultipleEvents() {
        when(outboxRepository.save(any(OutboxEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        OutboxEvent first = outboxService.recordEvent(
            "Order", "order-a", "ORDER_CREATED", "order.created", Map.of("k", "v"));
        OutboxEvent second = outboxService.recordEvent(
            "Order", "order-b", "ORDER_CREATED", "order.created", Map.of("k", "v"));

        assertThat(first.getEventId()).isNotEqualTo(second.getEventId());
    }

    @Test
    void should_throwSerialisationException_when_payloadCannotBeSerialised() throws Exception {
        ObjectMapper failing = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                throw new JsonProcessingException("boom") {};
            }
        };
        OutboxService bad = new OutboxService(outboxRepository, failing);

        assertThatThrownBy(() -> bad.recordEvent(
                "Order", "order-x", "ORDER_CREATED", "order.created", Map.of()))
            .isInstanceOf(OutboxService.OutboxSerializationException.class);

        // The row must NOT be persisted if we cannot serialise — caller's tx
        // will roll back on the surfaced exception.
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void should_acceptNullablePayloadFields_when_serialisationSucceeds() {
        when(outboxRepository.save(any(OutboxEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Map containing null values is supported by Jackson by default.
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("orderId", "order-2");
        payload.put("missing", null);

        outboxService.recordEvent(
            "Order", "order-2", "ORDER_UPDATED", "order.updated", payload);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("\"missing\":null");
    }
}
