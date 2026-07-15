package com.ecommerce.notificationservice.kafka;

import com.ecommerce.notificationservice.kafka.dedup.NotificationEventDeduplicator;
import com.ecommerce.notificationservice.kafka.event.CartAbandonedEvent;
import com.ecommerce.notificationservice.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CartAbandonedConsumer}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartAbandonedConsumer")
class CartAbandonedConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationEventDeduplicator deduplicator;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private CartAbandonedConsumer consumer;

    private CartAbandonedEvent event;
    private String payload;

    @BeforeEach
    void setUp() {
        lenient().when(deduplicator.claim(anyString(), anyString())).thenReturn(true);
        event = CartAbandonedEvent.builder()
                .cartId("101")
                .userId("user-1")
                .userEmail("user@example.com")
                .userName("Alice")
                .totalAmount(new BigDecimal("29.99"))
                .totalItems(2)
                .lineItems(List.of(CartAbandonedEvent.LineItem.builder()
                        .productId("p-1")
                        .productName("Widget")
                        .price(new BigDecimal("14.99"))
                        .quantity(2)
                        .build()))
                .abandonedAt(Instant.parse("2026-04-29T03:00:00Z"))
                .build();
        payload = "{\"cartId\":\"101\"}";
    }

    @Test
    @DisplayName("should_callNotificationService_when_eventValidAndNotDuplicate")
    void should_callNotificationService_when_eventValidAndNotDuplicate() throws Exception {
        when(objectMapper.readValue(anyString(), eq(CartAbandonedEvent.class))).thenReturn(event);

        consumer.handle(payload);

        verify(deduplicator).claim(eq("CART_ABANDONED:101"), eq("cart.abandoned"));
        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(notificationService, times(1)).sendNotification(
                eq("user-1"), eq("user@example.com"), eq("CART_ABANDONED"),
                vars.capture(), eq("101"), eq("CART"));
        Map<String, Object> captured = vars.getValue();
        assertThat(captured).containsEntry("totalItems", 2);
        assertThat(captured).containsEntry("totalAmount", new BigDecimal("29.99"));
        assertThat(captured.get("lineItems")).isInstanceOf(List.class);
    }

    @Test
    @DisplayName("should_skipSend_when_duplicateClaimLost")
    void should_skipSend_when_duplicateClaimLost() throws Exception {
        when(objectMapper.readValue(anyString(), eq(CartAbandonedEvent.class))).thenReturn(event);
        when(deduplicator.claim(eq("CART_ABANDONED:101"), anyString())).thenReturn(false);

        consumer.handle(payload);

        verify(notificationService, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap(), anyString(), anyString());
    }

    @Test
    @DisplayName("should_skipSend_when_userEmailMissing")
    void should_skipSend_when_userEmailMissing() throws Exception {
        event.setUserEmail(null);
        when(objectMapper.readValue(anyString(), eq(CartAbandonedEvent.class))).thenReturn(event);

        consumer.handle(payload);

        verify(notificationService, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap(), anyString(), anyString());
        verifyNoInteractions(deduplicator);
    }

    @Test
    @DisplayName("should_swallowDeserializationError_andNotCallService")
    void should_swallowDeserializationError_andNotCallService() throws Exception {
        when(objectMapper.readValue(anyString(), eq(CartAbandonedEvent.class)))
                .thenThrow(new RuntimeException("bad json"));

        consumer.handle("garbage");

        verify(notificationService, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap(), anyString(), anyString());
        verifyNoInteractions(deduplicator);
    }

    @Test
    @DisplayName("should_skipSend_when_eventHasNullCartId")
    void should_skipSend_when_eventHasNullCartId() throws Exception {
        event.setCartId(null);
        when(objectMapper.readValue(anyString(), eq(CartAbandonedEvent.class))).thenReturn(event);

        consumer.handle(payload);

        verify(notificationService, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap(), anyString(), anyString());
        verifyNoInteractions(deduplicator);
    }
}
