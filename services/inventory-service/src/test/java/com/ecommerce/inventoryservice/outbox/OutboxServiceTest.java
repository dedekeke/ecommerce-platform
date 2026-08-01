package com.ecommerce.inventoryservice.outbox;

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

        Map<String, Object> payload = Map.of("productId", 100, "currentQty", 2);

        OutboxEvent saved = outboxService.recordEvent(
            "Inventory", "100", "STOCK_LOW_DETECTED", "stock.low.detected", payload);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent persisted = captor.getValue();
        assertThat(persisted.getAggregateType()).isEqualTo("Inventory");
        assertThat(persisted.getAggregateId()).isEqualTo("100");
        assertThat(persisted.getEventType()).isEqualTo("STOCK_LOW_DETECTED");
        assertThat(persisted.getTopic()).isEqualTo("stock.low.detected");
        assertThat(persisted.getPayload())
            .contains("\"productId\":100")
            .contains("\"currentQty\":2");
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
            "Inventory", "100", "STOCK_LOW_DETECTED", "stock.low.detected", Map.of("k", "v"));
        OutboxEvent second = outboxService.recordEvent(
            "Inventory", "200", "STOCK_LOW_DETECTED", "stock.low.detected", Map.of("k", "v"));

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
                "Inventory", "100", "STOCK_LOW_DETECTED", "stock.low.detected", Map.of()))
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
        payload.put("productId", "100");
        payload.put("missing", null);

        outboxService.recordEvent(
            "Inventory", "100", "STOCK_REPLENISHED", "stock.replenished", payload);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("\"missing\":null");
    }
}
