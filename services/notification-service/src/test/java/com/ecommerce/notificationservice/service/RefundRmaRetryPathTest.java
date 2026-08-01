package com.ecommerce.notificationservice.service;

import com.ecommerce.notificationservice.domain.NotificationLog;
import com.ecommerce.notificationservice.domain.NotificationStatus;
import com.ecommerce.notificationservice.domain.NotificationTemplate;
import com.ecommerce.notificationservice.domain.NotificationType;
import com.ecommerce.notificationservice.repository.NotificationLogRepository;
import com.ecommerce.notificationservice.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression guard for the PR#131 review finding: the refund/RMA consumers hold
 * a <em>permanent</em> dedup claim, so a transient email failure must NOT be
 * left permanently FAILED (the retry scheduler only polls RETRYING). Both
 * consumers now route through {@link NotificationService#sendNotification}; this
 * proves that path leaves a scheduler-eligible RETRYING row for the refund and
 * RMA template codes on a transient failure.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Refund/RMA transient-failure retry path")
class RefundRmaRetryPathTest {

    @Mock private NotificationLogRepository logRepository;
    @Mock private NotificationTemplateRepository templateRepository;
    @Mock private EmailService emailService;
    @Mock private SmsService smsService;
    @Mock private PushService pushService;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                logRepository, templateRepository, emailService, smsService, pushService);
        when(logRepository.save(any(NotificationLog.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private NotificationTemplate emailTemplate(String code, String body) {
        return NotificationTemplate.builder()
                .code(code)
                .name(code)
                .type(NotificationType.EMAIL)
                .subject("subject")
                .body(body)
                .active(true)
                .build();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "REFUND_COMPLETED,refund-completed,REFUND",
            "RMA_REQUESTED,rma-requested,RMA",
            "RMA_COMPLETED,rma-completed,RMA",
            "RMA_REJECTED,rma-rejected,RMA"
    })
    @DisplayName("should_leaveRetryingRow_when_transientEmailFailure")
    void should_leaveRetryingRow_when_transientEmailFailure(String code, String body, String entityType) {
        when(templateRepository.findByCode(code)).thenReturn(Optional.of(emailTemplate(code, body)));
        doThrow(new RuntimeException("smtp down"))
                .when(emailService).sendEmail(anyString(), anyString(), eq(body), anyMap());

        notificationService.sendNotification(
                "user-1", "user@example.com", code, new HashMap<>(), "entity-1", entityType);

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository, org.mockito.Mockito.atLeast(2)).save(logCaptor.capture());

        List<NotificationLog> saves = logCaptor.getAllValues();
        NotificationLog last = saves.get(saves.size() - 1);
        // Scheduler-eligible: RETRYING with a nextRetryAt, NOT permanently FAILED.
        assertThat(last.getStatus()).isEqualTo(NotificationStatus.RETRYING);
        assertThat(last.getNextRetryAt()).isNotNull();
    }
}
