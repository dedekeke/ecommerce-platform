package com.ecommerce.notificationservice.kafka;

import com.ecommerce.notificationservice.domain.NotificationLog;
import com.ecommerce.notificationservice.domain.NotificationStatus;
import com.ecommerce.notificationservice.kafka.dedup.NotificationEventDeduplicator;
import com.ecommerce.notificationservice.kafka.event.RmaEvent;
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

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RmaEventConsumer — RMA topic handlers")
class RmaEventConsumerTest {

    @Mock private NotificationLogRepository notificationLogRepository;
    @Mock private NotificationEventDeduplicator deduplicator;
    @Mock private EmailService emailService;

    private ObjectMapper objectMapper;
    private RmaEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        consumer = new RmaEventConsumer(notificationLogRepository, deduplicator, emailService, objectMapper);
        lenient().when(deduplicator.claim(anyString(), anyString())).thenReturn(true);
        lenient().when(notificationLogRepository.save(any(NotificationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private RmaEvent sampleEvent() {
        return RmaEvent.builder()
            .rmaId("rma-1")
            .rmaNumber("RMA-XYZ")
            .orderId("order-1")
            .orderNumber("ORD-1")
            .userId("user-1")
            .userEmail("user@example.com")
            .returnLabelUrl("https://shipping.mock/labels/abc")
            .reason("size")
            .occurredAt(LocalDateTime.of(2026, 4, 29, 10, 0))
            .build();
    }

    // ---------- rma.requested ----------

    @Test
    @DisplayName("requested_should_claimRmaScopedKey")
    void requested_should_claimRmaScopedKey() throws Exception {
        consumer.handleRmaRequested(objectMapper.writeValueAsString(sampleEvent()));

        verify(deduplicator).claim(eq("RMA_REQUESTED:RMA-XYZ"), eq(RmaEventConsumer.RMA_REQUESTED_TOPIC));
    }

    @Test
    @DisplayName("requested_should_sendEmailWithLabelAndDetails")
    void requested_should_sendEmailWithLabelAndDetails() throws Exception {
        consumer.handleRmaRequested(objectMapper.writeValueAsString(sampleEvent()));

        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(emailService).sendEmail(
            eq("user@example.com"),
            eq("Your return has been authorized"),
            eq(RmaEventConsumer.TEMPLATE_REQUESTED),
            varsCaptor.capture());
        Map<String, Object> vars = varsCaptor.getValue();
        assertThat(vars).containsEntry("rmaNumber", "RMA-XYZ");
        assertThat(vars).containsEntry("orderNumber", "ORD-1");
        assertThat(vars).containsEntry("returnLabelUrl", "https://shipping.mock/labels/abc");
        assertThat(vars).containsEntry("reason", "size");
    }

    @Test
    @DisplayName("requested_should_persistSentLog_when_emailSucceeds")
    void requested_should_persistSentLog_when_emailSucceeds() throws Exception {
        consumer.handleRmaRequested(objectMapper.writeValueAsString(sampleEvent()));

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(logCaptor.capture());
        NotificationLog finalLog = logCaptor.getAllValues().get(1);
        assertThat(finalLog.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(finalLog.getRelatedEntityType()).isEqualTo("RMA");
        assertThat(finalLog.getRelatedEntityId()).isEqualTo("RMA-XYZ");
        assertThat(finalLog.getTemplateCode()).isEqualTo(RmaEventConsumer.CODE_REQUESTED);
    }

    @Test
    @DisplayName("requested_should_dropDuplicateEvent_when_claimLost")
    void requested_should_dropDuplicateEvent_when_claimLost() throws Exception {
        when(deduplicator.claim(eq("RMA_REQUESTED:RMA-XYZ"), anyString())).thenReturn(false);

        consumer.handleRmaRequested(objectMapper.writeValueAsString(sampleEvent()));

        verify(notificationLogRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    // ---------- rma.completed ----------

    @Test
    @DisplayName("completed_should_sendApprovalEmail")
    void completed_should_sendApprovalEmail() throws Exception {
        consumer.handleRmaCompleted(objectMapper.writeValueAsString(sampleEvent()));

        verify(deduplicator).claim(eq("RMA_COMPLETED:RMA-XYZ"), eq(RmaEventConsumer.RMA_COMPLETED_TOPIC));
        verify(emailService).sendEmail(
            eq("user@example.com"),
            eq("Your return has been completed"),
            eq(RmaEventConsumer.TEMPLATE_COMPLETED),
            any());
    }

    // ---------- rma.rejected ----------

    @Test
    @DisplayName("rejected_should_sendRejectionEmail_withInspectorNotes")
    void rejected_should_sendRejectionEmail_withInspectorNotes() throws Exception {
        RmaEvent ev = sampleEvent();
        ev.setOutcome("REJECTED");
        ev.setNotes("Item shows wear");

        consumer.handleRmaRejected(objectMapper.writeValueAsString(ev));

        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(emailService).sendEmail(
            eq("user@example.com"),
            eq("Your return has been rejected"),
            eq(RmaEventConsumer.TEMPLATE_REJECTED),
            varsCaptor.capture());
        assertThat(varsCaptor.getValue()).containsEntry("notes", "Item shows wear");
    }

    // ---------- key fallback ----------

    @Test
    @DisplayName("should_fallBackToOrderId_when_rmaNumberMissing")
    void should_fallBackToOrderId_when_rmaNumberMissing() throws Exception {
        RmaEvent ev = sampleEvent();
        ev.setRmaNumber(null);

        consumer.handleRmaRequested(objectMapper.writeValueAsString(ev));

        verify(deduplicator).claim(eq("RMA_REQUESTED:order-1"), eq(RmaEventConsumer.RMA_REQUESTED_TOPIC));
    }

    // ---------- failure / robustness ----------

    @Test
    @DisplayName("should_persistFailedLog_when_emailSenderThrows")
    void should_persistFailedLog_when_emailSenderThrows() throws Exception {
        org.mockito.Mockito.doThrow(new RuntimeException("smtp down"))
            .when(emailService).sendEmail(anyString(), anyString(), anyString(), any());

        consumer.handleRmaCompleted(objectMapper.writeValueAsString(sampleEvent()));

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(logCaptor.capture());
        NotificationLog finalLog = logCaptor.getAllValues().get(1);
        assertThat(finalLog.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(finalLog.getErrorMessage()).contains("smtp down");
    }

    @Test
    @DisplayName("should_skipEvent_when_userEmailMissing")
    void should_skipEvent_when_userEmailMissing() throws Exception {
        RmaEvent invalid = sampleEvent();
        invalid.setUserEmail(null);

        consumer.handleRmaRequested(objectMapper.writeValueAsString(invalid));

        verifyNoInteractions(deduplicator);
        verifyNoInteractions(notificationLogRepository);
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("should_swallowMalformedJson_so_partitionDoesNotStall")
    void should_swallowMalformedJson_so_partitionDoesNotStall() {
        consumer.handleRmaRequested("not-json");
        consumer.handleRmaCompleted("not-json");
        consumer.handleRmaRejected("not-json");

        verifyNoInteractions(deduplicator);
        verifyNoInteractions(notificationLogRepository);
        verifyNoInteractions(emailService);
    }
}
