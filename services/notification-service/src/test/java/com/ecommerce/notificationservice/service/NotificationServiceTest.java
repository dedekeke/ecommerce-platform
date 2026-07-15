package com.ecommerce.notificationservice.service;

import com.ecommerce.notificationservice.domain.NotificationLog;
import com.ecommerce.notificationservice.domain.NotificationStatus;
import com.ecommerce.notificationservice.domain.NotificationTemplate;
import com.ecommerce.notificationservice.domain.NotificationType;
import com.ecommerce.notificationservice.repository.NotificationLogRepository;
import com.ecommerce.notificationservice.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Test class for NotificationService
 * Following TDD principles with comprehensive unit tests
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationLogRepository logRepository;

    @Mock
    private NotificationTemplateRepository templateRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private SmsService smsService;

    @InjectMocks
    private NotificationService notificationService;

    private NotificationTemplate emailTemplate;
    private NotificationTemplate smsTemplate;
    private Map<String, Object> variables;

    @BeforeEach
    void setUp() {
        emailTemplate = NotificationTemplate.builder()
                .id("template1")
                .code("ORDER_CONFIRMATION")
                .name("Order Confirmation")
                .type(NotificationType.EMAIL)
                .subject("Order Confirmation - ${orderNumber}")
                .body("order-confirmation")
                .active(true)
                .build();

        smsTemplate = NotificationTemplate.builder()
                .id("template2")
                .code("SMS_NOTIFICATION")
                .name("SMS Notification")
                .type(NotificationType.SMS)
                .body("Your order ${orderNumber} has been confirmed")
                .active(true)
                .build();

        variables = new HashMap<>();
        variables.put("orderNumber", "ORD-12345");
        variables.put("userName", "John Doe");

        // In production Spring injects the async proxy here; default to the real
        // instance so non-retry tests exercise the actual send path.
        notificationService.setSelf(notificationService);
    }

    @Test
    void shouldSendEmailNotificationSuccessfully() {
        // Given
        when(templateRepository.findByCode("ORDER_CONFIRMATION"))
                .thenReturn(Optional.of(emailTemplate));
        when(logRepository.save(any(NotificationLog.class)))
                .thenAnswer(invocation -> {
                    NotificationLog log = invocation.getArgument(0);
                    log.setId("log123");
                    return log;
                });

        // When
        notificationService.sendNotification(
                "user123",
                "test@example.com",
                "ORDER_CONFIRMATION",
                variables,
                "order123",
                "ORDER"
        );

        // Then
        verify(emailService, timeout(1000)).sendEmail(
                eq("test@example.com"),
                eq("Order Confirmation - ${orderNumber}"),
                eq("order-confirmation"),
                eq(variables)
        );

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository, atLeast(2)).save(logCaptor.capture());

        NotificationLog savedLog = logCaptor.getAllValues().get(logCaptor.getAllValues().size() - 1);
        assertThat(savedLog.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(savedLog.getSentAt()).isNotNull();
    }

    @Test
    void shouldSendSmsNotificationSuccessfully() {
        // Given
        when(templateRepository.findByCode("SMS_NOTIFICATION"))
                .thenReturn(Optional.of(smsTemplate));
        when(logRepository.save(any(NotificationLog.class)))
                .thenAnswer(invocation -> {
                    NotificationLog log = invocation.getArgument(0);
                    log.setId("log123");
                    return log;
                });

        // When
        notificationService.sendNotification(
                "user123",
                "+1234567890",
                "SMS_NOTIFICATION",
                variables,
                "order123",
                "ORDER"
        );

        // Then
        verify(smsService, timeout(1000)).sendSms(
                eq("+1234567890"),
                eq("Your order ORD-12345 has been confirmed")
        );

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository, atLeast(2)).save(logCaptor.capture());
    }

    @Test
    void shouldThrowExceptionWhenTemplateNotFound() {
        // Given
        when(templateRepository.findByCode("NON_EXISTENT"))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> notificationService.sendNotification(
                "user123",
                "test@example.com",
                "NON_EXISTENT",
                variables,
                "order123",
                "ORDER"
        )).isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Template not found");

        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString(), any());
        verify(smsService, never()).sendSms(anyString(), anyString());
    }

    @Test
    void shouldNotSendNotificationWhenTemplateIsInactive() {
        // Given
        emailTemplate.setActive(false);
        when(templateRepository.findByCode("ORDER_CONFIRMATION"))
                .thenReturn(Optional.of(emailTemplate));

        // When
        notificationService.sendNotification(
                "user123",
                "test@example.com",
                "ORDER_CONFIRMATION",
                variables,
                "order123",
                "ORDER"
        );

        // Then
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString(), any());
        verify(logRepository, never()).save(any(NotificationLog.class));
    }

    @Test
    void shouldHandleFailedEmailAndScheduleRetry() {
        // Given
        when(templateRepository.findByCode("ORDER_CONFIRMATION"))
                .thenReturn(Optional.of(emailTemplate));
        when(logRepository.save(any(NotificationLog.class)))
                .thenAnswer(invocation -> {
                    NotificationLog log = invocation.getArgument(0);
                    log.setId("log123");
                    return log;
                });
        doThrow(new RuntimeException("Email service unavailable"))
                .when(emailService).sendEmail(anyString(), anyString(), anyString(), any());

        // When
        notificationService.sendNotification(
                "user123",
                "test@example.com",
                "ORDER_CONFIRMATION",
                variables,
                "order123",
                "ORDER"
        );

        // Then
        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository, atLeast(2)).save(logCaptor.capture());

        NotificationLog failedLog = logCaptor.getAllValues().get(logCaptor.getAllValues().size() - 1);
        assertThat(failedLog.getStatus()).isEqualTo(NotificationStatus.RETRYING);
        assertThat(failedLog.getErrorMessage()).contains("Email service unavailable");
        assertThat(failedLog.getNextRetryAt()).isNotNull();
    }

    @Test
    void shouldRetryNotification() {
        // Given
        NotificationLog failedLog = NotificationLog.builder()
                .id("log123")
                .userId("user123")
                .recipient("test@example.com")
                .templateCode("ORDER_CONFIRMATION")
                .variables(variables)
                .status(NotificationStatus.RETRYING)
                .retryCount(1)
                .relatedEntityId("order123")
                .relatedEntityType("ORDER")
                .build();

        when(logRepository.findById("log123"))
                .thenReturn(Optional.of(failedLog));
        when(templateRepository.findByCode("ORDER_CONFIRMATION"))
                .thenReturn(Optional.of(emailTemplate));

        // Snapshot the retry-update save at capture time, since the same
        // NotificationLog reference is mutated again by the subsequent
        // sendNotification call and ArgumentCaptor records by reference.
        java.util.concurrent.atomic.AtomicInteger capturedRetryCount = new java.util.concurrent.atomic.AtomicInteger(-1);
        java.util.concurrent.atomic.AtomicReference<NotificationStatus> capturedStatus = new java.util.concurrent.atomic.AtomicReference<>();
        when(logRepository.save(any(NotificationLog.class))).thenAnswer(invocation -> {
            NotificationLog arg = invocation.getArgument(0);
            if ("log123".equals(arg.getId()) && capturedRetryCount.get() == -1) {
                capturedRetryCount.set(arg.getRetryCount());
                capturedStatus.set(arg.getStatus());
            }
            return arg;
        });

        // When
        notificationService.retryNotification("log123");

        // Then
        // retryNotification must increment retry count and set status to RETRYING
        // before delegating to sendNotification.
        assertThat(capturedRetryCount.get()).isEqualTo(2);
        assertThat(capturedStatus.get()).isEqualTo(NotificationStatus.RETRYING);
    }

    @Test
    void should_dispatch_retry_send_through_async_proxy_not_self_invocation() {
        // Given — a distinct proxy stands in for the Spring async proxy.
        NotificationService asyncProxy = mock(NotificationService.class);
        notificationService.setSelf(asyncProxy);

        NotificationLog failedLog = NotificationLog.builder()
                .id("log123")
                .userId("user123")
                .recipient("test@example.com")
                .templateCode("ORDER_CONFIRMATION")
                .variables(variables)
                .status(NotificationStatus.RETRYING)
                .retryCount(1)
                .relatedEntityId("order123")
                .relatedEntityType("ORDER")
                .build();
        when(logRepository.findById("log123")).thenReturn(Optional.of(failedLog));
        when(logRepository.save(any(NotificationLog.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        notificationService.retryNotification("log123");

        // Then — the resend goes through the proxy (so @Async takes effect),
        // NOT inline via this.sendNotification.
        verify(asyncProxy).sendNotification(
                "user123", "test@example.com", "ORDER_CONFIRMATION",
                variables, "order123", "ORDER");
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString(), any());
    }

    @Test
    void shouldNotRetryWhenMaxAttemptsReached() {
        // Given
        NotificationLog failedLog = NotificationLog.builder()
                .id("log123")
                .userId("user123")
                .recipient("test@example.com")
                .templateCode("ORDER_CONFIRMATION")
                .status(NotificationStatus.FAILED)
                .retryCount(3)
                .build();

        when(logRepository.findById("log123"))
                .thenReturn(Optional.of(failedLog));

        // When/Then
        assertThatThrownBy(() -> notificationService.retryNotification("log123"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Max retry attempts reached");

        verify(templateRepository, never()).findByCode(anyString());
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString(), any());
    }

    @Test
    void shouldGetNotificationHistory() {
        // Given
        List<NotificationLog> logs = List.of(
                createNotificationLog("log1", "user1"),
                createNotificationLog("log2", "user2")
        );
        Page<NotificationLog> page = new PageImpl<>(logs);
        Pageable pageable = PageRequest.of(0, 10);

        when(logRepository.findAll(pageable)).thenReturn(page);

        // When
        Page<NotificationLog> result = notificationService.getNotificationHistory(pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        verify(logRepository).findAll(pageable);
    }

    @Test
    void shouldGetUserNotifications() {
        // Given
        List<NotificationLog> logs = List.of(
                createNotificationLog("log1", "user123"),
                createNotificationLog("log2", "user123")
        );
        Page<NotificationLog> page = new PageImpl<>(logs);
        Pageable pageable = PageRequest.of(0, 10);

        when(logRepository.findByUserId("user123", pageable)).thenReturn(page);

        // When
        Page<NotificationLog> result = notificationService.getUserNotifications("user123", pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(log -> log.getUserId().equals("user123"));
        verify(logRepository).findByUserId("user123", pageable);
    }

    @Test
    void shouldGetNotificationById() {
        // Given
        NotificationLog log = createNotificationLog("log123", "user123");
        when(logRepository.findById("log123")).thenReturn(Optional.of(log));

        // When
        NotificationLog result = notificationService.getNotification("log123");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("log123");
        verify(logRepository).findById("log123");
    }

    @Test
    void shouldThrowExceptionWhenNotificationNotFound() {
        // Given
        when(logRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> notificationService.getNotification("nonexistent"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Notification not found");
    }

    private NotificationLog createNotificationLog(String id, String userId) {
        return NotificationLog.builder()
                .id(id)
                .userId(userId)
                .recipient("test@example.com")
                .type(NotificationType.EMAIL)
                .templateCode("TEST")
                .status(NotificationStatus.SENT)
                .retryCount(0)
                .build();
    }
}
