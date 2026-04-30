package com.ecommerce.inventoryservice.saga.replenishment;

import com.ecommerce.inventoryservice.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes the two trigger events that drive the replenishment choreography.
 *
 * <p>This service is the <em>only</em> publisher of these two topics — peers
 * react and publish their own outcome topics. That asymmetry is what makes
 * this a choreography rather than a request/response: inventory-service does
 * not wait for, or even know about, the peers' replies.
 *
 * <p>Refactored to use the transactional outbox pattern. Public API is
 * unchanged so callers (StockLowDetector) continue to compile and behave
 * identically — but instead of calling {@code KafkaTemplate.send(...)} (the
 * dual-write that could lose events on crash between DB commit and broker
 * ack) we now record the event in the {@code outbox_event} table. The
 * downstream {@link com.ecommerce.inventoryservice.outbox.OutboxRelay} ships
 * rows to Kafka asynchronously.
 *
 * <p><strong>Transaction note:</strong> {@link OutboxService#recordEvent} is
 * {@code Propagation.MANDATORY}. The current caller — {@code
 * StockLowDetector#scan} — runs from {@code @Scheduled} and is NOT
 * transactional, so we open a transaction here ({@code @Transactional}
 * defaults to {@code REQUIRED}). If the detector ever moves into a
 * transactional service method, the publisher will join that existing
 * transaction instead of starting a new one — exactly what we want.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReplenishmentEventPublisher {

    /** Topic fired when stock first crosses below the reorder threshold. */
    public static final String TOPIC_STOCK_LOW_DETECTED = "stock.low.detected";

    /** Topic fired when stock rises back above the reorder threshold. */
    public static final String TOPIC_STOCK_REPLENISHED = "stock.replenished";

    private static final String AGGREGATE_TYPE = "Inventory";
    private static final String EVENT_TYPE_STOCK_LOW = "STOCK_LOW_DETECTED";
    private static final String EVENT_TYPE_STOCK_REPLENISHED = "STOCK_REPLENISHED";

    private final OutboxService outboxService;

    @Transactional
    public void publishStockLowDetected(StockLowDetectedEvent event) {
        log.info("Recording {} outbox row for productId={} sku={} qty={}/{}",
                TOPIC_STOCK_LOW_DETECTED, event.productId(), event.sku(),
                event.currentQty(), event.threshold());
        record(TOPIC_STOCK_LOW_DETECTED, EVENT_TYPE_STOCK_LOW,
                String.valueOf(event.productId()), event);
    }

    @Transactional
    public void publishStockReplenished(StockReplenishedEvent event) {
        log.info("Recording {} outbox row for productId={} sku={} qty={}",
                TOPIC_STOCK_REPLENISHED, event.productId(), event.sku(), event.currentQty());
        record(TOPIC_STOCK_REPLENISHED, EVENT_TYPE_STOCK_REPLENISHED,
                String.valueOf(event.productId()), event);
    }

    private void record(String topic, String eventType, String aggregateId, Object payload) {
        try {
            outboxService.recordEvent(AGGREGATE_TYPE, aggregateId, eventType, topic, payload);
        } catch (RuntimeException e) {
            // Re-throw: the caller's transaction MUST roll back if we cannot
            // record the event — that is the entire point of the outbox.
            log.error("Failed to record outbox event {} for aggregate {}", eventType, aggregateId, e);
            throw e;
        }
    }
}
