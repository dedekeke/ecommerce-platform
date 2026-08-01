package com.ecommerce.notificationservice.kafka;

import com.ecommerce.notificationservice.kafka.event.PromotionEvent;
import com.ecommerce.notificationservice.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private ObjectMapper objectMapper;

    private PromotionEventConsumer consumer;

    private PromotionEvent event;

    @BeforeEach
    void setUp() {
        consumer = new PromotionEventConsumer(
                notificationService, objectMapper, "announcements@ecommerce.local");

        event = PromotionEvent.builder()
                .eventType("PROMOTION_CREATED")
                .timestamp(LocalDateTime.now())
                .promoCode("WELCOME10")
                .name("Welcome 10%")
                .description("First-purchase discount")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
    }

    @Test
    void should_dispatchPromotionAnnouncement_when_eventReceived() throws Exception {
        when(objectMapper.readValue(anyString(), eq(PromotionEvent.class))).thenReturn(event);

        consumer.handlePromotionCreated("{}");

        ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).sendNotification(
                eq("system"),
                eq("announcements@ecommerce.local"),
                eq("PROMOTION_ANNOUNCEMENT"),
                variablesCaptor.capture(),
                eq("WELCOME10"),
                eq("PROMOTION")
        );
        assertThat(variablesCaptor.getValue().get("promoCode")).isEqualTo("WELCOME10");
        assertThat(variablesCaptor.getValue().get("name")).isEqualTo("Welcome 10%");
        assertThat(variablesCaptor.getValue().get("description")).isEqualTo("First-purchase discount");
    }

    @Test
    void should_useEventRecipient_when_userEmailProvided() throws Exception {
        event.setUserEmail("buyer@example.com");
        event.setUserId("user-42");
        event.setUserName("Buyer");
        when(objectMapper.readValue(anyString(), eq(PromotionEvent.class))).thenReturn(event);

        consumer.handlePromotionCreated("{}");

        verify(notificationService).sendNotification(
                eq("user-42"),
                eq("buyer@example.com"),
                eq("PROMOTION_ANNOUNCEMENT"),
                any(),
                eq("WELCOME10"),
                eq("PROMOTION")
        );
    }

    @Test
    void should_swallowMalformedPayload_when_parseFails() throws Exception {
        when(objectMapper.readValue(anyString(), eq(PromotionEvent.class)))
                .thenThrow(new RuntimeException("bad json"));

        consumer.handlePromotionCreated("not json");

        verify(notificationService, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap(), anyString(), anyString());
    }

    @Test
    void should_skipDispatch_when_eventIsNull() throws Exception {
        when(objectMapper.readValue(anyString(), eq(PromotionEvent.class))).thenReturn(null);

        consumer.handlePromotionCreated("{}");

        verify(notificationService, never()).sendNotification(
                anyString(), anyString(), anyString(), anyMap(), anyString(), anyString());
    }
}
