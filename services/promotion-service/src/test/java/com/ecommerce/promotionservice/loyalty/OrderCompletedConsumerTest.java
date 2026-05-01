package com.ecommerce.promotionservice.loyalty;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderCompletedConsumer}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCompletedConsumer")
class OrderCompletedConsumerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private LoyaltyService loyaltyService;

    @InjectMocks
    private OrderCompletedConsumer consumer;

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

        consumer.handle("{}");

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

        consumer.handle("{}");

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

        consumer.handle("{}");

        verify(loyaltyService, never()).recordSpend(anyString(), any(), any());
    }

    @Test
    @DisplayName("should_swallowDeserializationError")
    void should_swallowDeserializationError() throws Exception {
        when(objectMapper.readValue(anyString(), eq(OrderCompletedEvent.class)))
                .thenThrow(new RuntimeException("bad json"));

        consumer.handle("garbage");

        verify(loyaltyService, never()).recordSpend(anyString(), any(), any());
    }
}
