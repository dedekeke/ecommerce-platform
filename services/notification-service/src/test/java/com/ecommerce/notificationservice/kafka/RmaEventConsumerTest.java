package com.ecommerce.notificationservice.kafka;

import com.ecommerce.notificationservice.kafka.dedup.NotificationEventDeduplicator;
import com.ecommerce.notificationservice.kafka.event.RmaEvent;
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

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RmaEventConsumer — RMA topic handlers")
class RmaEventConsumerTest {

    @Mock private NotificationService notificationService;
    @Mock private NotificationEventDeduplicator deduplicator;

    private ObjectMapper objectMapper;
    private RmaEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        consumer = new RmaEventConsumer(notificationService, deduplicator, objectMapper);
        lenient().when(deduplicator.claim(anyString(), anyString())).thenReturn(true);
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
    @DisplayName("requested_should_routeThroughRetryCapableNotificationService_keyedByRmaNumber")
    void requested_should_routeThroughRetryCapableNotificationService_keyedByRmaNumber() throws Exception {
        consumer.handleRmaRequested(objectMapper.writeValueAsString(sampleEvent()));

        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).sendNotification(
            eq("user-1"),
            eq("user@example.com"),
            eq(RmaEventConsumer.CODE_REQUESTED),
            varsCaptor.capture(),
            eq("RMA-XYZ"),
            eq(RmaEventConsumer.ENTITY_TYPE));
        Map<String, Object> vars = varsCaptor.getValue();
        assertThat(vars).containsEntry("rmaNumber", "RMA-XYZ");
        assertThat(vars).containsEntry("orderNumber", "ORD-1");
        assertThat(vars).containsEntry("returnLabelUrl", "https://shipping.mock/labels/abc");
        assertThat(vars).containsEntry("reason", "size");
    }

    @Test
    @DisplayName("requested_should_dropDuplicateEvent_when_claimLost")
    void requested_should_dropDuplicateEvent_when_claimLost() throws Exception {
        when(deduplicator.claim(eq("RMA_REQUESTED:RMA-XYZ"), anyString())).thenReturn(false);

        consumer.handleRmaRequested(objectMapper.writeValueAsString(sampleEvent()));

        verifyNoInteractions(notificationService);
    }

    // ---------- rma.completed / rejected ----------

    @Test
    @DisplayName("completed_should_sendWithCompletedTemplateCode")
    void completed_should_sendWithCompletedTemplateCode() throws Exception {
        consumer.handleRmaCompleted(objectMapper.writeValueAsString(sampleEvent()));

        verify(deduplicator).claim(eq("RMA_COMPLETED:RMA-XYZ"), eq(RmaEventConsumer.RMA_COMPLETED_TOPIC));
        verify(notificationService).sendNotification(
            eq("user-1"), eq("user@example.com"), eq(RmaEventConsumer.CODE_COMPLETED),
            anyMap(), eq("RMA-XYZ"), eq(RmaEventConsumer.ENTITY_TYPE));
    }

    @Test
    @DisplayName("rejected_should_sendRejectionWithInspectorNotes")
    void rejected_should_sendRejectionWithInspectorNotes() throws Exception {
        RmaEvent ev = sampleEvent();
        ev.setOutcome("REJECTED");
        ev.setNotes("Item shows wear");

        consumer.handleRmaRejected(objectMapper.writeValueAsString(ev));

        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).sendNotification(
            eq("user-1"), eq("user@example.com"), eq(RmaEventConsumer.CODE_REJECTED),
            varsCaptor.capture(), eq("RMA-XYZ"), eq(RmaEventConsumer.ENTITY_TYPE));
        assertThat(varsCaptor.getValue()).containsEntry("notes", "Item shows wear");
    }

    // ---------- key resolution ----------

    @Test
    @DisplayName("should_skipEvent_when_rmaNumberMissing_toAvoidOrderIdCollision")
    void should_skipEvent_when_rmaNumberMissing_toAvoidOrderIdCollision() throws Exception {
        RmaEvent ev = sampleEvent();
        ev.setRmaNumber(null);

        consumer.handleRmaRequested(objectMapper.writeValueAsString(ev));

        verifyNoInteractions(deduplicator);
        verifyNoInteractions(notificationService);
    }

    // ---------- robustness ----------

    @Test
    @DisplayName("should_swallowDispatchError_so_partitionDoesNotStall")
    void should_swallowDispatchError_so_partitionDoesNotStall() throws Exception {
        doThrow(new RuntimeException("dispatch boom"))
            .when(notificationService).sendNotification(
                anyString(), anyString(), anyString(), anyMap(), anyString(), anyString());

        consumer.handleRmaCompleted(objectMapper.writeValueAsString(sampleEvent()));

        verify(notificationService).sendNotification(
            anyString(), anyString(), anyString(), anyMap(), anyString(), anyString());
    }

    @Test
    @DisplayName("should_skipEvent_when_userEmailMissing")
    void should_skipEvent_when_userEmailMissing() throws Exception {
        RmaEvent invalid = sampleEvent();
        invalid.setUserEmail(null);

        consumer.handleRmaRequested(objectMapper.writeValueAsString(invalid));

        verifyNoInteractions(deduplicator);
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("should_swallowMalformedJson_so_partitionDoesNotStall")
    void should_swallowMalformedJson_so_partitionDoesNotStall() {
        consumer.handleRmaRequested("not-json");
        consumer.handleRmaCompleted("not-json");
        consumer.handleRmaRejected("not-json");

        verifyNoInteractions(deduplicator);
        verifyNoInteractions(notificationService);
    }
}
