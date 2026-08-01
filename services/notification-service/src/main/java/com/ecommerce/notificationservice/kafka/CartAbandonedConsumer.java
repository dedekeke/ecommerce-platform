package com.ecommerce.notificationservice.kafka;

import com.ecommerce.notificationservice.kafka.dedup.NotificationEventDeduplicator;
import com.ecommerce.notificationservice.kafka.event.CartAbandonedEvent;
import com.ecommerce.notificationservice.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Consumer for {@code cart.abandoned} events (§3.10).
 *
 * <p>Sends a "you left items in your cart" email using the
 * {@code CART_ABANDONED} template. Idempotency is enforced insert-first via
 * {@link NotificationEventDeduplicator}: the {@code CART_ABANDONED:<cartId>}
 * dedup key is claimed on the unique {@code _id} index before dispatch, so a
 * concurrent replica or a Kafka redelivery cannot email the same cart twice.
 * This replaces the previous check-then-insert against the notification log.
 * Producer-side cool-off (7 days) is enforced by the {@code AbandonedCartScanner}.</p>
 *
 * <p>If the event arrives without {@code userEmail} we skip with a warning.
 * Email resolution from the userId is intentionally out of scope for this
 * consumer until a user-service lookup is added (tracked as a follow-up).</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CartAbandonedConsumer {

    static final String TOPIC = "cart.abandoned";
    static final String TEMPLATE_CODE = "CART_ABANDONED";
    static final String ENTITY_TYPE = "CART";

    private final NotificationService notificationService;
    private final NotificationEventDeduplicator deduplicator;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC, groupId = "notification-service")
    public void handle(String message) {
        CartAbandonedEvent event;
        try {
            event = objectMapper.readValue(message, CartAbandonedEvent.class);
        } catch (Exception ex) {
            log.error("Failed to deserialize cart.abandoned event payload", ex);
            return;
        }
        if (event == null || event.getCartId() == null) {
            log.warn("Discarding cart.abandoned event with missing cartId");
            return;
        }

        if (event.getUserEmail() == null || event.getUserEmail().isBlank()) {
            log.warn("Cart {} has no userEmail on cart.abandoned event; skipping send", event.getCartId());
            return;
        }

        if (!deduplicator.claim(TEMPLATE_CODE + ":" + event.getCartId(), TOPIC)) {
            log.info("Skipping duplicate cart.abandoned event for cartId={}", event.getCartId());
            return;
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", event.getUserName());
        variables.put("totalAmount", event.getTotalAmount());
        variables.put("totalItems", event.getTotalItems());
        variables.put("lineItems", event.getLineItems());
        variables.put("abandonedAt", event.getAbandonedAt());

        try {
            notificationService.sendNotification(
                    event.getUserId(),
                    event.getUserEmail(),
                    TEMPLATE_CODE,
                    variables,
                    event.getCartId(),
                    ENTITY_TYPE);
            log.info("Triggered cart abandonment email for cartId={} userId={}",
                    event.getCartId(), event.getUserId());
        } catch (Exception ex) {
            log.error("Failed to dispatch cart.abandoned notification for cartId={}",
                    event.getCartId(), ex);
        }
    }
}
