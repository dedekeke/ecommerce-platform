package com.ecommerce.recommendationservice.kafka;

import com.ecommerce.recommendationservice.kafka.event.OrderCreatedEvent;
import com.ecommerce.recommendationservice.service.CoOccurrenceUpdater;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Listens to the {@code order.created} topic and feeds product co-occurrences
 * into the recommendation matrix.
 *
 * <p>Failures are logged and swallowed: the goal is to never block the order
 * pipeline because of a recommendation-side issue. Idempotency is enforced
 * downstream in {@link CoOccurrenceUpdater}, so the consumer is safe to
 * commit-then-retry.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderCreatedConsumer {

    private final CoOccurrenceUpdater coOccurrenceUpdater;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.created", groupId = "recommendation-service")
    public void handleOrderCreated(String message) {
        try {
            log.debug("Received order.created payload");

            OrderCreatedEvent event = objectMapper.readValue(message, OrderCreatedEvent.class);
            if (event == null) {
                log.warn("Deserialised order.created event was null, skipping");
                return;
            }

            List<String> productIds = extractProductIds(event);

            coOccurrenceUpdater.ingestOrder(event.getOrderId(), event.getUserId(), productIds);

        } catch (Exception e) {
            log.error("Failed to process order.created event", e);
        }
    }

    private List<String> extractProductIds(OrderCreatedEvent event) {
        if (event.getItems() == null) {
            return List.of();
        }
        return event.getItems().stream()
                .filter(Objects::nonNull)
                .map(OrderCreatedEvent.OrderItem::getProductId)
                .filter(Objects::nonNull)
                .filter(p -> !p.isBlank())
                .toList();
    }
}
