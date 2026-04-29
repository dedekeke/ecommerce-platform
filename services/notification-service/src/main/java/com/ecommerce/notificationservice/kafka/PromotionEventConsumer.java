package com.ecommerce.notificationservice.kafka;

import com.ecommerce.notificationservice.kafka.event.PromotionEvent;
import com.ecommerce.notificationservice.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Consumes promotion lifecycle events and dispatches the
 * `PROMOTION_ANNOUNCEMENT` notification.
 *
 * If the event lacks a recipient (the publisher emits a broadcast),
 * the configured announcement recipient is used so the email still lands
 * in MailHog locally.
 */
@Component
@Slf4j
public class PromotionEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final String announcementRecipient;

    public PromotionEventConsumer(
            NotificationService notificationService,
            ObjectMapper objectMapper,
            @Value("${notification.email.announcement-recipient:announcements@ecommerce.local}")
            String announcementRecipient) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.announcementRecipient = announcementRecipient;
    }

    @KafkaListener(topics = "promotion.created", groupId = "notification-service")
    public void handlePromotionCreated(String message) {
        try {
            log.info("Received promotion.created event: {}", message);

            PromotionEvent event = objectMapper.readValue(message, PromotionEvent.class);
            if (event == null) {
                log.warn("Received null promotion event payload, skipping");
                return;
            }

            String recipient = (event.getUserEmail() != null && !event.getUserEmail().isBlank())
                    ? event.getUserEmail()
                    : announcementRecipient;

            String userId = event.getUserId() != null ? event.getUserId() : "system";

            Map<String, Object> variables = new HashMap<>();
            variables.put("promoCode", event.getPromoCode());
            variables.put("name", event.getName());
            variables.put("description", event.getDescription());
            variables.put("expiresAt", event.getExpiresAt());
            variables.put("userName",
                    event.getUserName() != null ? event.getUserName() : "Customer");

            notificationService.sendNotification(
                    userId,
                    recipient,
                    "PROMOTION_ANNOUNCEMENT",
                    variables,
                    event.getPromoCode(),
                    "PROMOTION"
            );

            log.info("Promotion announcement notification triggered for code: {}",
                    event.getPromoCode());

        } catch (Exception e) {
            log.error("Failed to process promotion.created event", e);
        }
    }
}
