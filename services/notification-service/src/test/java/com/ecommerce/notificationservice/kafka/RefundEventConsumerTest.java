package com.ecommerce.notificationservice.kafka;

import com.ecommerce.notificationservice.kafka.dedup.NotificationEventDeduplicator;
import com.ecommerce.notificationservice.kafka.event.RefundCompletedEvent;
import com.ecommerce.notificationservice.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefundEventConsumer — refund.completed handler")
class RefundEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationEventDeduplicator deduplicator;

    private ObjectMapper objectMapper;
    private RefundEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        consumer = new RefundEventConsumer(notificationService, deduplicator, objectMapper);
        lenient().when(deduplicator.claim(anyString(), anyString())).thenReturn(true);
    }

    private RefundCompletedEvent sampleEvent() {
        return RefundCompletedEvent.builder()
                .sagaId("saga-1")
                .orderId("order-1")
                .orderNumber("ORD-1")
                .userId("user-1")
                .userEmail("user@example.com")
                .refundTransactionId("rfd-tx-1")
                .amount(new BigDecimal("19.99"))
                .completedAt(LocalDateTime.of(2026, 4, 29, 10, 0))
                .build();
    }

    @Test
    @DisplayName("should_claimDedupKey_when_eventReceived")
    void should_claimDedupKey_when_eventReceived() throws Exception {
        consumer.handleRefundCompleted(objectMapper.writeValueAsString(sampleEvent()));

        verify(deduplicator).claim(eq("REFUND_COMPLETED:order-1"), eq(RefundEventConsumer.REFUND_COMPLETED_TOPIC));
    }

    @Test
    @DisplayName("should_routeThroughRetryCapableNotificationService_when_eventReceived")
    void should_routeThroughRetryCapableNotificationService_when_eventReceived() throws Exception {
        consumer.handleRefundCompleted(objectMapper.writeValueAsString(sampleEvent()));

        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).sendNotification(
                eq("user-1"),
                eq("user@example.com"),
                eq(RefundEventConsumer.TEMPLATE_CODE),
                varsCaptor.capture(),
                eq("order-1"),
                eq(RefundEventConsumer.ENTITY_TYPE));
        Map<String, Object> vars = varsCaptor.getValue();
        assertThat(vars).containsEntry("orderNumber", "ORD-1");
        assertThat(vars).containsEntry("orderId", "order-1");
        assertThat(vars).containsEntry("amount", new BigDecimal("19.99"));
        assertThat(vars).containsEntry("refundTransactionId", "rfd-tx-1");
    }

    @Test
    @DisplayName("should_dropDuplicateEvent_when_claimLost")
    void should_dropDuplicateEvent_when_claimLost() throws Exception {
        when(deduplicator.claim(eq("REFUND_COMPLETED:order-1"), anyString())).thenReturn(false);

        consumer.handleRefundCompleted(objectMapper.writeValueAsString(sampleEvent()));

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("should_swallowDispatchError_so_partitionDoesNotStall")
    void should_swallowDispatchError_so_partitionDoesNotStall() throws Exception {
        doThrow(new RuntimeException("dispatch boom"))
                .when(notificationService).sendNotification(
                        anyString(), anyString(), anyString(), anyMap(), anyString(), anyString());

        consumer.handleRefundCompleted(objectMapper.writeValueAsString(sampleEvent()));

        verify(notificationService).sendNotification(
                anyString(), anyString(), anyString(), anyMap(), anyString(), anyString());
    }

    @Test
    @DisplayName("should_skipEvent_when_userEmailMissing")
    void should_skipEvent_when_userEmailMissing() throws Exception {
        RefundCompletedEvent invalid = sampleEvent();
        invalid.setUserEmail(null);

        consumer.handleRefundCompleted(objectMapper.writeValueAsString(invalid));

        verifyNoInteractions(deduplicator);
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("should_swallowMalformedJson_so_partitionDoesNotStall")
    void should_swallowMalformedJson_so_partitionDoesNotStall() {
        consumer.handleRefundCompleted("not-json");

        verifyNoInteractions(deduplicator);
        verifyNoInteractions(notificationService);
    }
}
