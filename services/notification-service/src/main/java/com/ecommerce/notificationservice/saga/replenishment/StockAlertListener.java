package com.ecommerce.notificationservice.saga.replenishment;

import com.ecommerce.notificationservice.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Choreography participant — the notification side.
 *
 * <p>Sends the admin a low-stock email on {@code stock.low.detected} and a
 * follow-up "stock back" email on {@code stock.replenished}. Each path emits
 * its own outcome event so the audit trail is complete.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockAlertListener {

    static final String TOPIC_LOW = "stock.low.detected";
    static final String TOPIC_REPLENISHED = "stock.replenished";
    static final String TOPIC_ADMIN_NOTIFIED = "admin.notified.due-to-stock";
    static final String TOPIC_STOCK_BACK = "admin.stock-back.due-to-stock";

    private final EmailService emailService;
    private final ConsumedReplenishmentEventRepository consumedRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${notification.replenishment.admin-email:admin@ecommerce.com}")
    private String adminEmail;

    @KafkaListener(topics = TOPIC_LOW, groupId = "notification-service-replenishment")
    public void onStockLow(String message) {
        try {
            StockLowDetectedEvent event = objectMapper.readValue(message, StockLowDetectedEvent.class);
            if (alreadyConsumed(event.eventId())) {
                log.warn("Skipping duplicate {} eventId={}", TOPIC_LOW, event.eventId());
                return;
            }

            Map<String, Object> vars = new HashMap<>();
            vars.put("productId", event.productId());
            vars.put("sku", event.sku());
            vars.put("currentQty", event.currentQty());
            vars.put("threshold", event.threshold());
            emailService.sendEmail(adminEmail,
                    "Stock low for SKU " + event.sku(),
                    "stock-low-alert",
                    vars);

            recordConsumed(event.eventId(), TOPIC_LOW);
            kafkaTemplate.send(TOPIC_ADMIN_NOTIFIED, String.valueOf(event.productId()),
                    new AdminNotifiedEvent(UUID.randomUUID(), event.eventId(),
                            event.productId(), adminEmail, Instant.now()));
            log.info("Notified admin {} about low stock for productId={}", adminEmail, event.productId());
        } catch (Exception e) {
            log.error("Failed to process {} event", TOPIC_LOW, e);
        }
    }

    @KafkaListener(topics = TOPIC_REPLENISHED, groupId = "notification-service-replenishment")
    public void onStockReplenished(String message) {
        try {
            StockReplenishedEvent event = objectMapper.readValue(message, StockReplenishedEvent.class);
            if (alreadyConsumed(event.eventId())) {
                log.warn("Skipping duplicate {} eventId={}", TOPIC_REPLENISHED, event.eventId());
                return;
            }

            Map<String, Object> vars = new HashMap<>();
            vars.put("productId", event.productId());
            vars.put("sku", event.sku());
            vars.put("currentQty", event.currentQty());
            emailService.sendEmail(adminEmail,
                    "Stock replenished for SKU " + event.sku(),
                    "stock-back-alert",
                    vars);

            recordConsumed(event.eventId(), TOPIC_REPLENISHED);
            kafkaTemplate.send(TOPIC_STOCK_BACK, String.valueOf(event.productId()),
                    new StockBackInStockEvent(UUID.randomUUID(), event.eventId(),
                            event.productId(), adminEmail, Instant.now()));
            log.info("Notified admin {} that stock returned for productId={}", adminEmail, event.productId());
        } catch (Exception e) {
            log.error("Failed to process {} event", TOPIC_REPLENISHED, e);
        }
    }

    private boolean alreadyConsumed(UUID eventId) {
        if (eventId == null) {
            return false;
        }
        return consumedRepository.existsById(eventId);
    }

    private void recordConsumed(UUID eventId, String topic) {
        if (eventId == null) {
            return;
        }
        consumedRepository.save(ConsumedReplenishmentEvent.builder()
                .eventId(eventId)
                .topic(topic)
                .consumedAt(Instant.now())
                .build());
    }
}
