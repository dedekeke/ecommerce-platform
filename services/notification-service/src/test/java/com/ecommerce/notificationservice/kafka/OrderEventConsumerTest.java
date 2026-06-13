package com.ecommerce.notificationservice.kafka;

import com.ecommerce.notificationservice.kafka.event.OrderEvent;
import com.ecommerce.notificationservice.repository.NotificationLogRepository;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Test class for OrderEventConsumer
 * Following TDD principles
 */
@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @InjectMocks
    private OrderEventConsumer orderEventConsumer;

    private OrderEvent orderEvent;
    private String orderEventJson;

    @BeforeEach
    void setUp() {
        // Treat every event as non-duplicate so the consumer proceeds to send.
        lenient().when(notificationLogRepository.existsByRelatedEntityIdAndTemplateCodeAndStatusIn(
                anyString(), anyString(), any(List.class))).thenReturn(false);

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
        // Given
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class)))
                .thenReturn(orderEvent);

        // When
        orderEventConsumer.handleOrderCreated(orderEventJson);

        // Then
        ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).sendNotification(
                eq("user123"),
                eq("john.doe@example.com"),
                eq("ORDER_CONFIRMATION"),
                variablesCaptor.capture(),
                eq("order123"),
                eq("ORDER")
        );

        Map<String, Object> capturedVariables = variablesCaptor.getValue();
        assertThat(capturedVariables.get("orderNumber")).isEqualTo("ORD-12345");
        assertThat(capturedVariables.get("userName")).isEqualTo("John Doe");
        assertThat(capturedVariables.get("totalAmount")).isEqualTo(new BigDecimal("199.99"));
        assertThat(capturedVariables.get("shippingAddress")).isEqualTo("123 Main St, City, Country");
    }

    @Test
    void shouldHandlePaymentCompletedEvent() throws Exception {
        // Given
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class)))
                .thenReturn(orderEvent);

        // When
        orderEventConsumer.handlePaymentCompleted(orderEventJson);

        // Then
        ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).sendNotification(
                eq("user123"),
                eq("john.doe@example.com"),
                eq("PAYMENT_RECEIPT"),
                variablesCaptor.capture(),
                eq("order123"),
                eq("PAYMENT")
        );

        Map<String, Object> capturedVariables = variablesCaptor.getValue();
        assertThat(capturedVariables.get("orderNumber")).isEqualTo("ORD-12345");
        assertThat(capturedVariables.get("userName")).isEqualTo("John Doe");
        assertThat(capturedVariables.get("totalAmount")).isEqualTo(new BigDecimal("199.99"));
    }

    @Test
    void shouldHandleOrderShippedEvent() throws Exception {
        // Given
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class)))
                .thenReturn(orderEvent);

        // When
        orderEventConsumer.handleOrderShipped(orderEventJson);

        // Then
        ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).sendNotification(
                eq("user123"),
                eq("john.doe@example.com"),
                eq("SHIPPING_NOTIFICATION"),
                variablesCaptor.capture(),
                eq("order123"),
                eq("SHIPMENT")
        );

        Map<String, Object> capturedVariables = variablesCaptor.getValue();
        assertThat(capturedVariables.get("orderNumber")).isEqualTo("ORD-12345");
        assertThat(capturedVariables.get("userName")).isEqualTo("John Doe");
        assertThat(capturedVariables.get("shippingAddress")).isEqualTo("123 Main St, City, Country");
    }

    @Test
    void shouldHandleInvalidJsonGracefully() throws Exception {
        // Given
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class)))
                .thenThrow(new RuntimeException("Invalid JSON"));

        // When
        orderEventConsumer.handleOrderCreated("invalid json");

        // Then
        verify(notificationService, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap(), anyString(), anyString()
        );
    }

    @Test
    void shouldHandleNullEventGracefully() throws Exception {
        // Given
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class)))
                .thenReturn(null);

        // When/Then - Should not throw exception
        orderEventConsumer.handleOrderCreated(orderEventJson);
    }

    @Test
    void shouldContinueProcessingAfterNotificationFailure() throws Exception {
        // Given
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class)))
                .thenReturn(orderEvent);
        doThrow(new RuntimeException("Notification failed"))
                .when(notificationService).sendNotification(
                        anyString(), anyString(), anyString(), any(), anyString(), anyString()
                );

        // When - Should not throw exception
        orderEventConsumer.handleOrderCreated(orderEventJson);

        // Then
        verify(notificationService).sendNotification(
                anyString(), anyString(), anyString(), any(), anyString(), anyString()
        );
    }
}
