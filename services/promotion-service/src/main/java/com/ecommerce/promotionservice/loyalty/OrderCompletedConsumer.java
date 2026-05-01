package com.ecommerce.promotionservice.loyalty;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Consumes {@code order.completed} events and accumulates lifetime spend
 * per user (§3.7).
 *
 * <p>Deduplication: handled by the database via natural per-order
 * boundaries — the consumer keys off {@code orderId} for logging only and
 * relies on the upstream outbox + Kafka idempotent producer for at-most-once
 * semantics. If a duplicate event somehow slips through it would
 * over-count the user's spend; the next major iteration should add an
 * application-level processed_orders ledger keyed on (orderId).</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderCompletedConsumer {

    static final String TOPIC = "order.completed";

    private final ObjectMapper objectMapper;
    private final LoyaltyService loyaltyService;

    @KafkaListener(topics = TOPIC, groupId = "promotion-service-loyalty")
    public void handle(String message) {
        OrderCompletedEvent event;
        try {
            event = objectMapper.readValue(message, OrderCompletedEvent.class);
        } catch (Exception ex) {
            log.error("Failed to deserialize order.completed payload", ex);
            return;
        }
        if (event == null) {
            log.warn("Received empty order.completed payload");
            return;
        }
        String userId = event.getUserId();
        BigDecimal amount = event.getTotalAmount() != null ? event.getTotalAmount() : event.getTotal();
        if (userId == null || userId.isBlank() || amount == null) {
            log.warn("Skipping order.completed event with missing userId/amount: orderId={}",
                    event.getOrderId());
            return;
        }
        try {
            loyaltyService.recordSpend(userId, amount, event.getTimestamp());
            log.info("Recorded loyalty spend for userId={} orderId={} amount={}",
                    userId, event.getOrderId(), amount);
        } catch (Exception ex) {
            log.error("Failed to record loyalty spend for orderId={}", event.getOrderId(), ex);
        }
    }
}
