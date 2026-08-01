package com.ecommerce.notificationservice.controller;

import com.ecommerce.notificationservice.domain.NotificationLog;
import com.ecommerce.notificationservice.domain.NotificationStatus;
import com.ecommerce.notificationservice.domain.NotificationType;
import com.ecommerce.notificationservice.repository.NotificationLogRepository;
import com.ecommerce.notificationservice.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test class for NotificationController
 * Following TDD principles
 */
@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationLogRepository notificationLogRepository;

    @MockBean
    private NotificationService notificationService;

    private NotificationLog sampleLog;

    @BeforeEach
    void setUp() {
        sampleLog = NotificationLog.builder()
                .id("log123")
                .userId("user123")
                .recipient("test@example.com")
                .type(NotificationType.EMAIL)
                .templateCode("ORDER_CONFIRMATION")
                .subject("Order Confirmation")
                .status(NotificationStatus.SENT)
                .retryCount(0)
                .build();
    }

    @Test
    void shouldGetAllNotifications() throws Exception {
        // Given
        List<NotificationLog> logs = List.of(sampleLog);
        Page<NotificationLog> page = new PageImpl<>(logs, PageRequest.of(0, 20), logs.size());

        when(notificationLogRepository.findAll(any(Pageable.class)))
                .thenReturn(page);

        // When/Then
        mockMvc.perform(get("/api/notifications")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value("log123"))
                .andExpect(jsonPath("$.content[0].userId").value("user123"))
                .andExpect(jsonPath("$.content[0].status").value("SENT"));

        verify(notificationLogRepository).findAll(any(Pageable.class));
    }

    @Test
    void shouldGetNotificationById() throws Exception {
        // Given
        when(notificationLogRepository.findById("log123"))
                .thenReturn(Optional.of(sampleLog));

        // When/Then
        mockMvc.perform(get("/api/notifications/log123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("log123"))
                .andExpect(jsonPath("$.userId").value("user123"))
                .andExpect(jsonPath("$.recipient").value("test@example.com"))
                .andExpect(jsonPath("$.status").value("SENT"));

        verify(notificationLogRepository).findById("log123");
    }

    @Test
    void shouldReturn404WhenNotificationNotFound() throws Exception {
        // Given
        when(notificationLogRepository.findById("nonexistent"))
                .thenReturn(Optional.empty());

        // When/Then
        mockMvc.perform(get("/api/notifications/nonexistent"))
                .andExpect(status().isNotFound());

        verify(notificationLogRepository).findById("nonexistent");
    }

    @Test
    void shouldGetNotificationsByUserId() throws Exception {
        // Given
        List<NotificationLog> logs = List.of(sampleLog);
        Page<NotificationLog> page = new PageImpl<>(logs, PageRequest.of(0, 20), logs.size());

        when(notificationLogRepository.findByUserId(eq("user123"), any(Pageable.class)))
                .thenReturn(page);

        // When/Then
        mockMvc.perform(get("/api/notifications/user/user123")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("log123"))
                .andExpect(jsonPath("$[0].userId").value("user123"));

        verify(notificationLogRepository).findByUserId(eq("user123"), any(Pageable.class));
    }

    @Test
    void shouldGetFailedNotifications() throws Exception {
        // Given
        NotificationLog failedLog = NotificationLog.builder()
                .id("failed1")
                .userId("user123")
                .recipient("test@example.com")
                .type(NotificationType.EMAIL)
                .status(NotificationStatus.FAILED)
                .errorMessage("Email service unavailable")
                .retryCount(1)
                .build();

        List<NotificationLog> failedLogs = List.of(failedLog);
        Page<NotificationLog> page = new PageImpl<>(failedLogs, PageRequest.of(0, 20), failedLogs.size());

        when(notificationLogRepository.findByStatus(eq(NotificationStatus.FAILED), any(Pageable.class)))
                .thenReturn(page);

        // When/Then
        mockMvc.perform(get("/api/notifications/failed")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("failed1"))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].errorMessage").value("Email service unavailable"));

        verify(notificationLogRepository).findByStatus(eq(NotificationStatus.FAILED), any(Pageable.class));
    }

    @Test
    void shouldRetryFailedNotification() throws Exception {
        // Given
        NotificationLog failedLog = NotificationLog.builder()
                .id("failed1")
                .userId("user123")
                .recipient("test@example.com")
                .templateCode("ORDER_CONFIRMATION")
                .status(NotificationStatus.FAILED)
                .retryCount(1)
                .build();

        when(notificationLogRepository.findById("failed1"))
                .thenReturn(Optional.of(failedLog));
        doNothing().when(notificationService).retryNotification("failed1");

        // When/Then
        mockMvc.perform(post("/api/notifications/retry/failed1"))
                .andExpect(status().isOk())
                .andExpect(content().string("Notification retry initiated"));

        verify(notificationLogRepository).findById("failed1");
        verify(notificationService).retryNotification("failed1");
    }

    @Test
    void shouldRetryRetryingNotification() throws Exception {
        // Given
        NotificationLog retryingLog = NotificationLog.builder()
                .id("retry1")
                .userId("user123")
                .recipient("test@example.com")
                .templateCode("ORDER_CONFIRMATION")
                .status(NotificationStatus.RETRYING)
                .retryCount(1)
                .build();

        when(notificationLogRepository.findById("retry1"))
                .thenReturn(Optional.of(retryingLog));
        doNothing().when(notificationService).retryNotification("retry1");

        // When/Then
        mockMvc.perform(post("/api/notifications/retry/retry1"))
                .andExpect(status().isOk())
                .andExpect(content().string("Notification retry initiated"));

        verify(notificationService).retryNotification("retry1");
    }

    @Test
    void shouldRejectRetryForSentNotification() throws Exception {
        // Given
        NotificationLog sentLog = NotificationLog.builder()
                .id("sent1")
                .userId("user123")
                .recipient("test@example.com")
                .status(NotificationStatus.SENT)
                .retryCount(0)
                .build();

        when(notificationLogRepository.findById("sent1"))
                .thenReturn(Optional.of(sentLog));

        // When/Then
        mockMvc.perform(post("/api/notifications/retry/sent1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Notification is not in FAILED or RETRYING status"));

        verify(notificationLogRepository).findById("sent1");
        verify(notificationService, never()).retryNotification(anyString());
    }

    @Test
    void shouldReturn404WhenRetryingNonExistentNotification() throws Exception {
        // Given
        when(notificationLogRepository.findById("nonexistent"))
                .thenReturn(Optional.empty());

        // When/Then
        mockMvc.perform(post("/api/notifications/retry/nonexistent"))
                .andExpect(status().isNotFound());

        verify(notificationLogRepository).findById("nonexistent");
        verify(notificationService, never()).retryNotification(anyString());
    }

    @Test
    void shouldHandleRetryException() throws Exception {
        // Given
        NotificationLog failedLog = NotificationLog.builder()
                .id("failed1")
                .userId("user123")
                .recipient("test@example.com")
                .templateCode("ORDER_CONFIRMATION")
                .status(NotificationStatus.FAILED)
                .retryCount(3)
                .build();

        when(notificationLogRepository.findById("failed1"))
                .thenReturn(Optional.of(failedLog));
        doThrow(new RuntimeException("Max retry attempts reached"))
                .when(notificationService).retryNotification("failed1");

        // When/Then
        mockMvc.perform(post("/api/notifications/retry/failed1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Failed to retry notification: Max retry attempts reached"));

        verify(notificationService).retryNotification("failed1");
    }

    @Test
    void shouldUseDefaultPaginationParameters() throws Exception {
        // Given
        List<NotificationLog> logs = List.of(sampleLog);
        Page<NotificationLog> page = new PageImpl<>(logs, PageRequest.of(0, 20), logs.size());

        when(notificationLogRepository.findAll(any(Pageable.class)))
                .thenReturn(page);

        // When/Then
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk());

        verify(notificationLogRepository).findAll(PageRequest.of(0, 20));
    }
}
