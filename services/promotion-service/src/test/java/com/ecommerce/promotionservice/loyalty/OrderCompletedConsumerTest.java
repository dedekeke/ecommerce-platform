package com.ecommerce.promotionservice.loyalty;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderCompletedConsumer}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCompletedConsumer")
class OrderCompletedConsumerTest {

    private static final String EVENT_ID = "3f2504e0-4f89-41d3-9a0c-0305e82c3301";

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private LoyaltyService loyaltyService;

    @Mock
    private ProcessedLoyaltyEventRepository processedEventRepository;

    @InjectMocks
    private OrderCompletedConsumer consumer;

    private static byte[] header(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should_recordSpend_when_eventHasUserIdAndTotalAmount")
    void should_recordSpend_when_eventHasUserIdAndTotalAmount() throws Exception {
        OrderCompletedEvent event = OrderCompletedEvent.builder()
                .orderId("order-1")
                .userId("user-1")
                .totalAmount(new BigDecimal("199.99"))
                .timestamp(LocalDateTime.parse("2026-04-29T10:00:00"))
                .build();
        when(objectMapper.readValue(anyString(), eq(OrderCompletedEvent.class))).thenReturn(event);

        consumer.handle("{}", header(EVENT_ID));

        verify(loyaltyService, times(1)).recordSpend(
                eq("user-1"),
                eq(new BigDecimal("199.99")),
                eq(LocalDateTime.parse("2026-04-29T10:00:00")));
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

        verify(loyaltyService).recordSpend(eq("user-1"), eq(new BigDecimal("50.00")), eq(null));
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

        verify(loyaltyService, never()).recordSpend(anyString(), any(), any());
    }

    @Test
    @DisplayName("should_swallowDeserializationError")
    void should_swallowDeserializationError() throws Exception {
        when(objectMapper.readValue(anyString(), eq(OrderCompletedEvent.class)))
                .thenThrow(new RuntimeException("bad json"));

        consumer.handle("garbage", header(EVENT_ID));

        verify(loyaltyService, never()).recordSpend(anyString(), any(), any());
        verifyNoInteractions(processedEventRepository);
    }

    // ---------------------------------------------------------------------
    // Idempotence / deduplication on the outbox-event-id header (PR#112 gate)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("should_recordSpendOnce_when_sameOutboxEventIdDeliveredTwice")
    void should_recordSpendOnce_when_sameOutboxEventIdDeliveredTwice() throws Exception {
        OrderCompletedEvent event = OrderCompletedEvent.builder()
                .orderId("order-1")
                .userId("user-1")
                .totalAmount(new BigDecimal("199.99"))
                .timestamp(LocalDateTime.parse("2026-04-29T10:00:00"))
                .build();
        when(objectMapper.readValue(anyString(), eq(OrderCompletedEvent.class))).thenReturn(event);
        // First delivery: not yet processed. Second (duplicate): already recorded.
        when(processedEventRepository.existsById(EVENT_ID)).thenReturn(false, true);

        consumer.handle("{}", header(EVENT_ID));
        consumer.handle("{}", header(EVENT_ID));

        // Lifetime spend must be incremented exactly once despite duplicate delivery.
        verify(loyaltyService, times(1)).recordSpend(
                eq("user-1"), eq(new BigDecimal("199.99")), any());
        // The processed-event ledger row is written exactly once (first delivery).
        verify(processedEventRepository, times(1)).save(any(ProcessedLoyaltyEvent.class));
    }

    @Test
    @DisplayName("should_persistProcessedEventRow_when_firstDelivery")
    void should_persistProcessedEventRow_when_firstDelivery() throws Exception {
        OrderCompletedEvent event = OrderCompletedEvent.builder()
                .orderId("order-1")
                .userId("user-1")
                .totalAmount(new BigDecimal("10.00"))
                .build();
        when(objectMapper.readValue(anyString(), eq(OrderCompletedEvent.class))).thenReturn(event);
        when(processedEventRepository.existsById(EVENT_ID)).thenReturn(false);

        consumer.handle("{}", header(EVENT_ID));

        ArgumentCaptor<ProcessedLoyaltyEvent> captor =
                ArgumentCaptor.forClass(ProcessedLoyaltyEvent.class);
        verify(processedEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(EVENT_ID);
        assertThat(captor.getValue().getConsumedAt()).isNotNull();
    }

    @Test
    @DisplayName("should_skipSpend_when_duplicateEventIdAlreadyProcessed")
    void should_skipSpend_when_duplicateEventIdAlreadyProcessed() throws Exception {
        OrderCompletedEvent event = OrderCompletedEvent.builder()
                .orderId("order-1")
                .userId("user-1")
                .totalAmount(new BigDecimal("199.99"))
                .build();
        when(objectMapper.readValue(anyString(), eq(OrderCompletedEvent.class))).thenReturn(event);
        when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

        consumer.handle("{}", header(EVENT_ID));

        verify(loyaltyService, never()).recordSpend(anyString(), any(), any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("should_processWithWarningAndNoDedup_when_outboxEventIdHeaderMissing")
    void should_processWithWarningAndNoDedup_when_outboxEventIdHeaderMissing() throws Exception {
        // Legacy / manual publish has no outbox-event-id header. We accept the
        // event (process-with-warning) but cannot dedup it — documented behavior.
        OrderCompletedEvent event = OrderCompletedEvent.builder()
                .orderId("order-legacy")
                .userId("user-1")
                .totalAmount(new BigDecimal("25.00"))
                .build();
        when(objectMapper.readValue(anyString(), eq(OrderCompletedEvent.class))).thenReturn(event);

        consumer.handle("{}", null);

        verify(loyaltyService, times(1)).recordSpend(eq("user-1"), eq(new BigDecimal("25.00")), any());
        verifyNoInteractions(processedEventRepository);
    }

    @Test
    @DisplayName("should_processWithWarning_when_outboxEventIdHeaderBlank")
    void should_processWithWarning_when_outboxEventIdHeaderBlank() throws Exception {
        OrderCompletedEvent event = OrderCompletedEvent.builder()
                .orderId("order-blank")
                .userId("user-1")
                .totalAmount(new BigDecimal("25.00"))
                .build();
        when(objectMapper.readValue(anyString(), eq(OrderCompletedEvent.class))).thenReturn(event);

        consumer.handle("{}", header("   "));

        verify(loyaltyService, times(1)).recordSpend(eq("user-1"), eq(new BigDecimal("25.00")), any());
        verifyNoInteractions(processedEventRepository);
    }
}
