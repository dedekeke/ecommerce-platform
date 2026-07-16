package com.ecommerce.notificationservice.kafka;

import com.ecommerce.notificationservice.kafka.dedup.NotificationEventDeduplicator;
import com.ecommerce.notificationservice.kafka.event.OrderEvent;
import com.ecommerce.notificationservice.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OrderEventConsumer}. Dedup is exercised here at the
 * mock level (claim returns true/false); the real unique-index guarantee is
 * proven in {@code NotificationEventDeduplicatorIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private NotificationEventDeduplicator deduplicator;

    @InjectMocks
    private OrderEventConsumer orderEventConsumer;

    private OrderEvent orderEvent;
    private String orderEventJson;

    @BeforeEach
    void setUp() {
        lenient().when(deduplicator.claim(anyString(), anyString())).thenReturn(true);

        orderEvent = new OrderEvent();
        orderEvent.setOrderId("order123");
        orderEvent.setOrderNumber("ORD-12345");
        orderEvent.setUserId("user123");
        orderEvent.setUserName("John Doe");
        orderEvent.setUserEmail("john.doe@example.com");
        orderEvent.setTotalAmount(new BigDecimal("199.99"));
        orderEvent.setShippingAddress("123 Main St, City, Country");

        orderEventJson = "{\"orderId\":\"order123\",\"orderNumber\":\"ORD-12345\"}";
    }

    @Test
    void shouldHandleOrderCreatedEvent() throws Exception {
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class))).thenReturn(orderEvent);

        orderEventConsumer.handleOrderCreated(orderEventJson);

        verify(deduplicator).claim(eq("ORDER_CONFIRMATION:order123"), eq("order.created"));
        ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).sendNotification(
                eq("user123"), eq("john.doe@example.com"), eq("ORDER_CONFIRMATION"),
                variablesCaptor.capture(), eq("order123"), eq("ORDER"));

        Map<String, Object> capturedVariables = variablesCaptor.getValue();
        assertThat(capturedVariables.get("orderNumber")).isEqualTo("ORD-12345");
        assertThat(capturedVariables.get("userName")).isEqualTo("John Doe");
        assertThat(capturedVariables.get("totalAmount")).isEqualTo(new BigDecimal("199.99"));
        assertThat(capturedVariables.get("shippingAddress")).isEqualTo("123 Main St, City, Country");
    }

    @Test
    void should_sendEmailOnly_when_phoneAndTokenAbsent() throws Exception {
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class))).thenReturn(orderEvent);

        orderEventConsumer.handleOrderCreated(orderEventJson);

        verify(notificationService).sendNotification(
                anyString(), anyString(), anyString(), anyMap(), anyString(), anyString());
        verify(notificationService, never()).sendNotification(
                anyString(), anyString(), eq("ORDER_CONFIRMATION_SMS"), anyMap(), anyString(), anyString());
        verify(notificationService, never()).sendNotification(
                anyString(), anyString(), eq("ORDER_CONFIRMATION_PUSH"), anyMap(), anyString(), anyString());
    }

    @Test
    void should_alsoSendSms_when_phonePresentOnOrderCreated() throws Exception {
        orderEvent.setUserPhone("+15551234567");
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class))).thenReturn(orderEvent);

        orderEventConsumer.handleOrderCreated(orderEventJson);

        verify(notificationService).sendNotification(
                eq("user123"), eq("john.doe@example.com"), eq("ORDER_CONFIRMATION"),
                anyMap(), eq("order123"), eq("ORDER"));
        verify(notificationService).sendNotification(
                eq("user123"), eq("+15551234567"), eq("ORDER_CONFIRMATION_SMS"),
                anyMap(), eq("order123"), eq("ORDER"));
    }

    @Test
    void should_alsoSendPush_when_deviceTokenPresentOnShipped() throws Exception {
        orderEvent.setUserDeviceToken("device-token-xyz");
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class))).thenReturn(orderEvent);

        orderEventConsumer.handleOrderShipped(orderEventJson);

        verify(notificationService).sendNotification(
                eq("user123"), eq("john.doe@example.com"), eq("SHIPPING_NOTIFICATION"),
                anyMap(), eq("order123"), eq("SHIPMENT"));
        verify(notificationService).sendNotification(
                eq("user123"), eq("device-token-xyz"), eq("SHIPPING_NOTIFICATION_PUSH"),
                anyMap(), eq("order123"), eq("SHIPMENT"));
    }

    @Test
    void should_sendAllThreeChannels_when_phoneAndTokenPresentOnShipped() throws Exception {
        orderEvent.setUserPhone("+15551234567");
        orderEvent.setUserDeviceToken("device-token-xyz");
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class))).thenReturn(orderEvent);

        orderEventConsumer.handleOrderShipped(orderEventJson);

        verify(notificationService).sendNotification(
                anyString(), eq("john.doe@example.com"), eq("SHIPPING_NOTIFICATION"),
                anyMap(), anyString(), anyString());
        verify(notificationService).sendNotification(
                anyString(), eq("+15551234567"), eq("SHIPPING_NOTIFICATION_SMS"),
                anyMap(), anyString(), anyString());
        verify(notificationService).sendNotification(
                anyString(), eq("device-token-xyz"), eq("SHIPPING_NOTIFICATION_PUSH"),
                anyMap(), anyString(), anyString());
    }

    @Test
    void shouldHandlePaymentCompletedEvent() throws Exception {
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class))).thenReturn(orderEvent);

        orderEventConsumer.handlePaymentCompleted(orderEventJson);

        verify(deduplicator).claim(eq("PAYMENT_RECEIPT:order123"), eq("payment.completed"));
        verify(notificationService).sendNotification(
                eq("user123"), eq("john.doe@example.com"), eq("PAYMENT_RECEIPT"),
                anyMap(), eq("order123"), eq("PAYMENT"));
    }

    @Test
    void shouldHandleOrderShippedEvent() throws Exception {
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class))).thenReturn(orderEvent);

        orderEventConsumer.handleOrderShipped(orderEventJson);

        verify(deduplicator).claim(eq("SHIPPING_NOTIFICATION:order123"), eq("order.shipped"));
        verify(notificationService).sendNotification(
                eq("user123"), eq("john.doe@example.com"), eq("SHIPPING_NOTIFICATION"),
                anyMap(), eq("order123"), eq("SHIPMENT"));
    }

    @Test
    void shouldSendOnlyOnce_whenDuplicateDelivered() throws Exception {
        // First delivery wins the claim, the redelivery loses it.
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class))).thenReturn(orderEvent);
        when(deduplicator.claim(eq("ORDER_CONFIRMATION:order123"), eq("order.created")))
                .thenReturn(true, false);

        orderEventConsumer.handleOrderCreated(orderEventJson);
        orderEventConsumer.handleOrderCreated(orderEventJson);

        verify(notificationService, times(1)).sendNotification(
                anyString(), anyString(), anyString(), anyMap(), anyString(), anyString());
    }

    @Test
    void shouldSkipSend_whenDuplicate() throws Exception {
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class))).thenReturn(orderEvent);
        when(deduplicator.claim(anyString(), anyString())).thenReturn(false);

        orderEventConsumer.handleOrderCreated(orderEventJson);

        verify(notificationService, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap(), anyString(), anyString());
    }

    @Test
    void shouldHandleInvalidJsonGracefully() throws Exception {
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class)))
                .thenThrow(new RuntimeException("Invalid JSON"));

        orderEventConsumer.handleOrderCreated("invalid json");

        verifyNoInteractions(deduplicator);
        verify(notificationService, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap(), anyString(), anyString());
    }

    @Test
    void shouldSkip_whenEventMissingOrderId() throws Exception {
        OrderEvent noId = new OrderEvent();
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class))).thenReturn(noId);

        orderEventConsumer.handleOrderCreated(orderEventJson);

        verifyNoInteractions(deduplicator);
        verify(notificationService, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap(), anyString(), anyString());
    }

    @Test
    void shouldHandleNullEventGracefully() throws Exception {
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class))).thenReturn(null);

        orderEventConsumer.handleOrderCreated(orderEventJson);

        verifyNoInteractions(deduplicator);
    }

    @Test
    void shouldContinueProcessingAfterNotificationFailure() throws Exception {
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class))).thenReturn(orderEvent);
        doThrow(new RuntimeException("Notification failed"))
                .when(notificationService).sendNotification(
                        anyString(), anyString(), anyString(), any(), anyString(), anyString());

        orderEventConsumer.handleOrderCreated(orderEventJson);

        verify(notificationService).sendNotification(
                anyString(), anyString(), anyString(), any(), anyString(), anyString());
    }
}
