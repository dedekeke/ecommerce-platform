package com.ecommerce.inventoryservice.saga.replenishment;

import com.ecommerce.inventoryservice.domain.entity.Inventory;
import com.ecommerce.inventoryservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodic scanner that emits the choreography trigger events.
 *
 * <p>Every 30s this looks at every inventory row and:
 * <ul>
 *   <li>If {@code availableQty &lt;= reorderLevel} and we have not already
 *       reported this product as low — publish {@code stock.low.detected}.</li>
 *   <li>If {@code availableQty &gt; reorderLevel} and the product was
 *       previously reported as low — publish {@code stock.replenished}
 *       (the compensation trigger).</li>
 * </ul>
 *
 * <p><strong>Edge-trigger semantics</strong> matter — without the in-memory
 * "already reported" set we would re-fire the same event every 30s and DoS the
 * peers. In a multi-instance deployment this set must move to Redis or a
 * shared table; for this learning exercise the local set is sufficient and
 * the trade-off is called out for clarity.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockLowDetector {

    private final InventoryRepository inventoryRepository;
    private final ReplenishmentEventPublisher publisher;

    /** Products we have already announced as low and are awaiting replenishment for. */
    private final Set<String> reportedLow = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedDelayString = "${inventory.replenishment.scan-interval-ms:30000}")
    public void scan() {
        log.debug("Replenishment choreography scan starting");

        Set<String> seenLowThisRun = new HashSet<>();

        for (Inventory inv : inventoryRepository.findAll()) {
            int available = inv.getAvailableQuantity();
            int threshold = inv.getReorderLevel();
            String productId = inv.getProductId();

            if (available <= threshold) {
                seenLowThisRun.add(productId);
                if (reportedLow.add(productId)) {
                    publishLow(inv, available, threshold);
                }
            }
        }

        for (String productId : Set.copyOf(reportedLow)) {
            if (!seenLowThisRun.contains(productId)) {
                inventoryRepository.findByProductId(productId)
                        .ifPresent(this::publishReplenished);
                reportedLow.remove(productId);
            }
        }
    }

    private void publishLow(Inventory inv, int available, int threshold) {
        Long productIdLong = parseProductId(inv.getProductId());
        if (productIdLong == null) {
            return;
        }
        StockLowDetectedEvent event = new StockLowDetectedEvent(
                UUID.randomUUID(),
                productIdLong,
                inv.getSku(),
                available,
                threshold,
                Instant.now()
        );
        publisher.publishStockLowDetected(event);
    }

    private void publishReplenished(Inventory inv) {
        Long productIdLong = parseProductId(inv.getProductId());
        if (productIdLong == null) {
            return;
        }
        StockReplenishedEvent event = new StockReplenishedEvent(
                UUID.randomUUID(),
                productIdLong,
                inv.getSku(),
                inv.getAvailableQuantity(),
                Instant.now()
        );
        publisher.publishStockReplenished(event);
    }

    private Long parseProductId(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            log.warn("Skipping inventory row with non-numeric productId={} — choreography contract requires Long", raw);
            return null;
        }
    }
}
