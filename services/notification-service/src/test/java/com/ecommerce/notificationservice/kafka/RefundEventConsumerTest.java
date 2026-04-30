package com.ecommerce.notificationservice.kafka;

import com.ecommerce.notificationservice.domain.NotificationLog;
import com.ecommerce.notificationservice.domain.NotificationStatus;
import com.ecommerce.notificationservice.kafka.event.RefundCompletedEvent;
import com.ecommerce.notificationservice.repository.NotificationLogRepository;
import com.ecommerce.notificationservice.service.EmailService;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefundEventConsumer — refund.completed handler")
class RefundEventConsumerTest {

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private EmailService emailService;

    private ObjectMapper objectMapper;
    private RefundEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        consumer = new RefundEventConsumer(notificationLogRepository, emailService, objectMapper);
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
    @DisplayName("should_sendEmailWithCorrectVariables_when_eventReceived")
    void should_sendEmailWithCorrectVariables_when_eventReceived() throws Exception {
        when(notificationLogRepository.existsByRelatedEntityIdAndTemplateCodeAndStatusIn(
                eq("order-1"), eq(RefundEventConsumer.TEMPLATE_CODE), anyList())).thenReturn(false);
        when(notificationLogRepository.save(any(NotificationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        consumer.handleRefundCompleted(objectMapper.writeValueAsString(sampleEvent()));

        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(emailService).sendEmail(
                eq("user@example.com"),
                eq("Your refund has been processed"),
                eq(RefundEventConsumer.TEMPLATE_NAME),
                varsCaptor.capture()
        );
        Map<String, Object> vars = varsCaptor.getValue();
        assertThat(vars).containsEntry("orderNumber", "ORD-1");
        assertThat(vars).containsEntry("orderId", "order-1");
        assertThat(vars).containsEntry("amount", new BigDecimal("19.99"));
        assertThat(vars).containsEntry("refundTransactionId", "rfd-tx-1");
    }

    @Test
    @DisplayName("should_persistSentLog_when_emailSendSucceeds")
    void should_persistSentLog_when_emailSendSucceeds() throws Exception {
        when(notificationLogRepository.existsByRelatedEntityIdAndTemplateCodeAndStatusIn(
                anyString(), anyString(), anyList())).thenReturn(false);
        when(notificationLogRepository.save(any(NotificationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        consumer.handleRefundCompleted(objectMapper.writeValueAsString(sampleEvent()));

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(logCaptor.capture());

        NotificationLog finalLog = logCaptor.getAllValues().get(1);
        assertThat(finalLog.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(finalLog.getSentAt()).isNotNull();
        assertThat(finalLog.getRelatedEntityType()).isEqualTo("REFUND");
        assertThat(finalLog.getTemplateCode()).isEqualTo(RefundEventConsumer.TEMPLATE_CODE);
    }

    @Test
    @DisplayName("should_dropDuplicateEvent_when_orderAlreadyNotified")
    void should_dropDuplicateEvent_when_orderAlreadyNotified() throws Exception {
        when(notificationLogRepository.existsByRelatedEntityIdAndTemplateCodeAndStatusIn(
                eq("order-1"), eq(RefundEventConsumer.TEMPLATE_CODE), anyList())).thenReturn(true);

        consumer.handleRefundCompleted(objectMapper.writeValueAsString(sampleEvent()));

        verify(notificationLogRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("should_persistFailedLog_when_emailSenderThrows")
    void should_persistFailedLog_when_emailSenderThrows() throws Exception {
        when(notificationLogRepository.existsByRelatedEntityIdAndTemplateCodeAndStatusIn(
                anyString(), anyString(), anyList())).thenReturn(false);
        when(notificationLogRepository.save(any(NotificationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("smtp down"))
                .when(emailService).sendEmail(anyString(), anyString(), anyString(), any());

        consumer.handleRefundCompleted(objectMapper.writeValueAsString(sampleEvent()));

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(logCaptor.capture());

        NotificationLog finalLog = logCaptor.getAllValues().get(1);
        assertThat(finalLog.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(finalLog.getErrorMessage()).contains("smtp down");
    }

    @Test
    @DisplayName("should_skipEvent_when_userEmailMissing")
    void should_skipEvent_when_userEmailMissing() throws Exception {
        RefundCompletedEvent invalid = sampleEvent();
        invalid.setUserEmail(null);

        consumer.handleRefundCompleted(objectMapper.writeValueAsString(invalid));

        verifyNoInteractions(notificationLogRepository);
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("should_swallowMalformedJson_so_partitionDoesNotStall")
    void should_swallowMalformedJson_so_partitionDoesNotStall() {
        consumer.handleRefundCompleted("not-json");
        verifyNoInteractions(notificationLogRepository);
        verifyNoInteractions(emailService);
    }
}
