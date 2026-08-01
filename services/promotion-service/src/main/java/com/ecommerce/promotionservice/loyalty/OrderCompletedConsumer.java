package com.ecommerce.promotionservice.loyalty;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * Consumes {@code order.completed} events and accumulates lifetime spend
 * per user (§3.7).
 *
 * <p><b>Idempotency.</b> The order-service transactional-outbox relay (PR#112)
 * delivers <em>at-least-once</em> with a stable per-event id in the
 * {@code outbox-event-id} header. Without dedup a redelivery would increment a
 * user's lifetime spend twice, inflating their loyalty tier and handing out
 * unearned discounts. This consumer is the consumer-side exactly-once safety
 * net the outbox redesign relies on: it delegates to {@link OrderCompletedProcessor},
 * which claims the event id in the {@link ProcessedLoyaltyEvent} ledger and
 * accumulates spend in one transaction. A duplicate fails the ledger INSERT
 * ({@link DataIntegrityViolationException}) and rolls the spend back; we catch
 * that here, confirm via the ledger that the eventId is actually already
 * committed, and only then return normally — so a benign duplicate is an
 * idempotent success that neither double-counts nor triggers a retry/DLT. Any
 * <em>other</em> data-integrity failure (data truncation, a different
 * constraint) rolls the ledger row back with it, so the confirmation fails and
 * the exception propagates rather than being mistaken for "already processed".
 *
 * <p><b>Error handling.</b> Genuine failures (DB down, etc.) propagate and are
 * handled by the container's {@code DefaultErrorHandler} (see
 * {@code KafkaConsumerErrorConfig}): retried with backoff, then routed to the
 * {@code <topic>.DLT} dead-letter topic once retries are exhausted, so a poison
 * message never spins the partition forever.
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
    private final OrderCompletedProcessor processor;
    private final ProcessedLoyaltyEventRepository processedEventRepository;

    @KafkaListener(topics = TOPIC, groupId = "promotion-service-loyalty")
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
        }

        try {
            processor.process(userId, amount, event.getTimestamp(), eventId);
            log.info("Recorded loyalty spend for userId={} orderId={} amount={} eventId={}",
                    userId, event.getOrderId(), amount, eventId);
        } catch (DataIntegrityViolationException ex) {
            // Narrow the swallow to a TRUE duplicate only. The insert-first ledger
            // shares one transaction with the spend, so a committed ledger row for
            // this eventId can only mean a prior delivery already succeeded — a
            // genuine duplicate. Any other data-integrity failure (truncation,
            // NOT NULL, ...) rolls the ledger row back too, so existsById stays
            // false and we must NOT treat it as processed: rethrow so the container
            // error handler retries / routes to the DLT instead of silently
            // dropping the spend.
            if (eventId != null && processedEventRepository.existsById(eventId)) {
                log.warn("Skipping duplicate order.completed eventId={} orderId={}",
                        eventId, event.getOrderId());
                return;
            }
            throw ex;
        }
    }

    private String decodeEventId(byte[] header) {
        if (header == null || header.length == 0) {
            return null;
        }
        String value = new String(header, StandardCharsets.UTF_8).trim();
        return value.isEmpty() ? null : value;
    }
}
