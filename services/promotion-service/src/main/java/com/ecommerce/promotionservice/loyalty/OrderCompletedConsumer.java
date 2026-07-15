package com.ecommerce.promotionservice.loyalty;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Consumes {@code order.completed} events and accumulates lifetime spend
 * per user (§3.7).
 *
 * <p><b>Idempotency.</b> The order-service transactional-outbox relay (PR#112)
 * delivers <em>at-least-once</em> with a stable per-event id in the
 * {@code outbox-event-id} header. Without dedup a redelivery would increment a
 * user's lifetime spend twice, inflating their loyalty tier and handing out
 * unearned discounts. This consumer is the consumer-side exactly-once safety
 * net the outbox redesign relies on: it records each processed id in
 * {@link ProcessedLoyaltyEvent} and drops duplicates. The whole handler runs
 * in one transaction, so the spend increment and the ledger insert commit
 * atomically — under concurrent replicas the duplicate insert violates the
 * primary key, rolls the loser's transaction back, and the spend is not
 * double-counted. Mirrors the choreography dedup in
 * {@link com.ecommerce.promotionservice.saga.replenishment.PromotionStockListener}.
 *
 * <p><b>Missing header.</b> Legacy or manual publishes without the
 * {@code outbox-event-id} header cannot be deduplicated. Such events are
 * processed with a WARN (documented process-with-warning behavior) rather than
 * dropped, so a mis-published event still counts rather than silently
 * vanishing.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderCompletedConsumer {

    static final String TOPIC = "order.completed";
    static final String EVENT_ID_HEADER = "outbox-event-id";

    private final ObjectMapper objectMapper;
    private final LoyaltyService loyaltyService;
    private final ProcessedLoyaltyEventRepository processedEventRepository;

    @KafkaListener(topics = TOPIC, groupId = "promotion-service-loyalty")
    @Transactional
    public void handle(@Payload String message,
                       @Header(name = EVENT_ID_HEADER, required = false) byte[] eventIdHeader) {
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

        String eventId = decodeEventId(eventIdHeader);
        if (eventId == null) {
            log.warn("order.completed without {} header (legacy/manual publish) — "
                    + "processing WITHOUT dedup: orderId={}", EVENT_ID_HEADER, event.getOrderId());
        } else if (processedEventRepository.existsById(eventId)) {
            log.warn("Skipping duplicate order.completed eventId={} orderId={}",
                    eventId, event.getOrderId());
            return;
        }

        loyaltyService.recordSpend(userId, amount, event.getTimestamp());
        if (eventId != null) {
            processedEventRepository.save(ProcessedLoyaltyEvent.builder()
                    .eventId(eventId)
                    .topic(TOPIC)
                    .consumedAt(Instant.now())
                    .build());
        }
        log.info("Recorded loyalty spend for userId={} orderId={} amount={} eventId={}",
                userId, event.getOrderId(), amount, eventId);
    }

    private String decodeEventId(byte[] header) {
        if (header == null || header.length == 0) {
            return null;
        }
        String value = new String(header, StandardCharsets.UTF_8).trim();
        return value.isEmpty() ? null : value;
    }
}
