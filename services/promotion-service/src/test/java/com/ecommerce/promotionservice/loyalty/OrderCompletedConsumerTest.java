package com.ecommerce.promotionservice.loyalty;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderCompletedConsumer} — the messaging adapter.
 * The transactional dedup/spend logic is verified against a real datastore in
 * {@link OrderCompletedProcessorTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCompletedConsumer")
class OrderCompletedConsumerTest {

    private static final String EVENT_ID = "3f2504e0-4f89-41d3-9a0c-0305e82c3301";

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private OrderCompletedProcessor processor;

    @InjectMocks
    private OrderCompletedConsumer consumer;

    private static byte[] header(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should_delegateToProcessorWithEventId_when_eventValidAndHeaderPresent")
    void should_delegateToProcessorWithEventId_when_eventValidAndHeaderPresent() throws Exception {
        OrderCompletedEvent event = OrderCompletedEvent.builder()
                .orderId("order-1")
                .userId("user-1")
                .totalAmount(new BigDecimal("199.99"))
                .timestamp(LocalDateTime.parse("2026-04-29T10:00:00"))
                .build();
        when(objectMapper.readValue(anyString(), eq(OrderCompletedEvent.class))).thenReturn(event);

        consumer.handle("{}", header(EVENT_ID));

        verify(processor, times(1)).process(
                eq("user-1"),
                eq(new BigDecimal("199.99")),
                eq(LocalDateTime.parse("2026-04-29T10:00:00")),
                eq(EVENT_ID));
    }

    @Test
    @DisplayName("should_fallBackToTotal_when_totalAmountMissing")
    void should_fallBackToTotal_when_totalAmountMissing() throws Exception {
        OrderCompletedEvent event = OrderCompletedEvent.builder()
                .orderId("order-1")
                .userId("user-1")
                .total(new BigDecimal("50.00"))
                .build();
        when(objectMapper.readValue(anyString(), eq(OrderCompletedEvent.class))).thenReturn(event);

        consumer.handle("{}", header(EVENT_ID));

        verify(processor).process(eq("user-1"), eq(new BigDecimal("50.00")), isNull(), eq(EVENT_ID));
    }

    @Test
    @DisplayName("should_skip_when_userIdMissing")
    void should_skip_when_userIdMissing() throws Exception {
        OrderCompletedEvent event = OrderCompletedEvent.builder()
                .orderId("order-1")
                .userId(null)
                .totalAmount(new BigDecimal("10"))
                .build();
        when(objectMapper.readValue(anyString(), eq(OrderCompletedEvent.class))).thenReturn(event);

        consumer.handle("{}", header(EVENT_ID));

        verifyNoInteractions(processor);
    }

    @Test
    @DisplayName("should_swallowDeserializationError")
    void should_swallowDeserializationError() throws Exception {
        when(objectMapper.readValue(anyString(), eq(OrderCompletedEvent.class)))
                .thenThrow(new RuntimeException("bad json"));

        consumer.handle("garbage", header(EVENT_ID));

        verifyNoInteractions(processor);
    }

    // ---------------------------------------------------------------------
    // Idempotence / dedup on the outbox-event-id header (PR#112 gate)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("should_swallowDuplicateAndNotRetry_when_processorReportsDuplicateKey")
    void should_swallowDuplicateAndNotRetry_when_processorReportsDuplicateKey() throws Exception {
        OrderCompletedEvent event = OrderCompletedEvent.builder()
                .orderId("order-1")
                .userId("user-1")
                .totalAmount(new BigDecimal("199.99"))
                .build();
        when(objectMapper.readValue(anyString(), eq(OrderCompletedEvent.class))).thenReturn(event);
        // A redelivery / concurrent replica: the ledger INSERT fails on the PK.
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(processor).process(anyString(), any(), any(), eq(EVENT_ID));

        // Must NOT rethrow: a benign duplicate is an idempotent success, so the
        // offset is committed and the container does not retry or route to DLT.
        assertThatCode(() -> consumer.handle("{}", header(EVENT_ID))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should_propagate_when_processorFailsWithTransientError")
    void should_propagate_when_processorFailsWithTransientError() throws Exception {
        OrderCompletedEvent event = OrderCompletedEvent.builder()
                .orderId("order-1")
                .userId("user-1")
                .totalAmount(new BigDecimal("199.99"))
                .build();
        when(objectMapper.readValue(anyString(), eq(OrderCompletedEvent.class))).thenReturn(event);
        // A genuine failure (e.g. DB unavailable) must reach the container error
        // handler so it can back off, retry, and eventually route to the DLT.
        doThrow(new RuntimeException("DB down"))
                .when(processor).process(anyString(), any(), any(), eq(EVENT_ID));

        assertThatThrownBy(() -> consumer.handle("{}", header(EVENT_ID)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB down");
    }

    @Test
    @DisplayName("should_passNullEventId_when_outboxEventIdHeaderMissing")
    void should_passNullEventId_when_outboxEventIdHeaderMissing() throws Exception {
        // Legacy / manual publish has no outbox-event-id header. We accept the
        // event (process-with-warning) but cannot dedup it — documented behavior.
        OrderCompletedEvent event = OrderCompletedEvent.builder()
                .orderId("order-legacy")
                .userId("user-1")
                .totalAmount(new BigDecimal("25.00"))
                .build();
        when(objectMapper.readValue(anyString(), eq(OrderCompletedEvent.class))).thenReturn(event);

        consumer.handle("{}", null);

        verify(processor).process(eq("user-1"), eq(new BigDecimal("25.00")), any(), isNull());
    }

    @Test
    @DisplayName("should_passNullEventId_when_outboxEventIdHeaderBlank")
    void should_passNullEventId_when_outboxEventIdHeaderBlank() throws Exception {
        OrderCompletedEvent event = OrderCompletedEvent.builder()
                .orderId("order-blank")
                .userId("user-1")
                .totalAmount(new BigDecimal("25.00"))
                .build();
        when(objectMapper.readValue(anyString(), eq(OrderCompletedEvent.class))).thenReturn(event);

        consumer.handle("{}", header("   "));

        verify(processor).process(eq("user-1"), eq(new BigDecimal("25.00")), any(), isNull());
    }
}
