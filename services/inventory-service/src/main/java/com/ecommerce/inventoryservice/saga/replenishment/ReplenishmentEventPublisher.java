package com.ecommerce.inventoryservice.saga.replenishment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes the two trigger events that drive the replenishment choreography.
 *
 * <p>This service is the <em>only</em> publisher of these two topics — peers
 * react and publish their own outcome topics. That asymmetry is what makes
 * this a choreography rather than a request/response: inventory-service does
 * not wait for, or even know about, the peers' replies.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReplenishmentEventPublisher {

    /** Topic fired when stock first crosses below the reorder threshold. */
    public static final String TOPIC_STOCK_LOW_DETECTED = "stock.low.detected";

    /** Topic fired when stock rises back above the reorder threshold. */
    public static final String TOPIC_STOCK_REPLENISHED = "stock.replenished";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishStockLowDetected(StockLowDetectedEvent event) {
        log.info("Publishing {} for productId={} sku={} qty={}/{}",
                TOPIC_STOCK_LOW_DETECTED, event.productId(), event.sku(),
                event.currentQty(), event.threshold());
        kafkaTemplate.send(TOPIC_STOCK_LOW_DETECTED, String.valueOf(event.productId()), event);
    }

    public void publishStockReplenished(StockReplenishedEvent event) {
        log.info("Publishing {} for productId={} sku={} qty={}",
                TOPIC_STOCK_REPLENISHED, event.productId(), event.sku(), event.currentQty());
        kafkaTemplate.send(TOPIC_STOCK_REPLENISHED, String.valueOf(event.productId()), event);
    }
}
