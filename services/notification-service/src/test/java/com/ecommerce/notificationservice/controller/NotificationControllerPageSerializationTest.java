package com.ecommerce.notificationservice.controller;

import com.ecommerce.notificationservice.domain.NotificationLog;
import com.ecommerce.notificationservice.domain.NotificationStatus;
import com.ecommerce.notificationservice.domain.NotificationType;
import com.ecommerce.notificationservice.repository.NotificationLogRepository;
import com.ecommerce.notificationservice.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Serialization contract tests for {@link NotificationController#getAllNotifications}.
 *
 * <p>Regression coverage for the bug where the endpoint returned a raw Spring
 * Data {@code PageImpl}, leaking the internal {@code pageable}/{@code sort}
 * structure and hard-failing serialization on the Spring Boot 3.3+ upgrade
 * path. The endpoint must emit a stable paged envelope.
 */
@WebMvcTest(NotificationController.class)
class NotificationControllerPageSerializationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationLogRepository notificationLogRepository;

    @MockBean
    private NotificationService notificationService;

    private static NotificationLog sampleLog() {
        return NotificationLog.builder()
                .id("log123")
                .userId("user123")
                .recipient("test@example.com")
                .type(NotificationType.EMAIL)
                .status(NotificationStatus.SENT)
                .build();
    }

    @Test
    void should_omitPageableAndSort_when_getAllNotifications() throws Exception {
        Page<NotificationLog> page = new PageImpl<>(List.of(sampleLog()), PageRequest.of(0, 20), 1);
        when(notificationLogRepository.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andExpect(jsonPath("$.sort").doesNotExist());
    }

    @Test
    void should_exposeStablePageFields_when_getAllNotifications() throws Exception {
        Page<NotificationLog> page = new PageImpl<>(List.of(sampleLog()), PageRequest.of(0, 20), 1);
        when(notificationLogRepository.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value("log123"))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.numberOfElements").value(1))
                .andExpect(jsonPath("$.empty").value(false));
    }
}
