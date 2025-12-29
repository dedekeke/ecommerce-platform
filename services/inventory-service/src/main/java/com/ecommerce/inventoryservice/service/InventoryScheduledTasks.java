package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.domain.entity.Inventory;
import com.ecommerce.inventoryservice.repository.InventoryReservationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class InventoryScheduledTasks {

    private final InventoryService inventoryService;
    private final InventoryReservationRepository reservationRepository;
    private final Counter expiredReservationsCounter;
    private final Counter cleanedReservationsCounter;
    private final Counter restockAlertsCounter;
    private final Timer releaseExpiredTimer;
    private final Timer cleanupOldTimer;
    private final Timer restockAlertTimer;
    private final int cleanupDaysThreshold;
    private final AtomicLong lowStockItemsCount = new AtomicLong(0);

    public InventoryScheduledTasks(
            InventoryService inventoryService,
            InventoryReservationRepository reservationRepository,
            MeterRegistry meterRegistry,
            @Value("${inventory.scheduled.cleanup-days-threshold:90}") int cleanupDaysThreshold) {
        this.inventoryService = inventoryService;
        this.reservationRepository = reservationRepository;
        this.cleanupDaysThreshold = cleanupDaysThreshold;
        this.expiredReservationsCounter = Counter.builder("inventory.reservations.expired")
                .description("Number of expired reservations released")
                .register(meterRegistry);
        this.cleanedReservationsCounter = Counter.builder("inventory.reservations.cleaned")
                .description("Number of old reservation records cleaned up")
                .register(meterRegistry);
        this.restockAlertsCounter = Counter.builder("inventory.restock.alerts")
                .description("Number of restock alerts generated")
                .register(meterRegistry);
        this.releaseExpiredTimer = Timer.builder("inventory.job.release_expired.duration")
                .description("Duration of release expired reservations job")
                .register(meterRegistry);
        this.cleanupOldTimer = Timer.builder("inventory.job.cleanup_old.duration")
                .description("Duration of cleanup old reservations job")
                .register(meterRegistry);
        this.restockAlertTimer = Timer.builder("inventory.job.restock_alert.duration")
                .description("Duration of restock alert job")
                .register(meterRegistry);

        Gauge.builder("inventory.low_stock.items", lowStockItemsCount, AtomicLong::get)
                .description("Number of items with low stock")
                .register(meterRegistry);
    }

    /**
     * Auto-release expired reservations
     * Runs every 5 minutes by default
     */
    @Scheduled(cron = "${inventory.scheduled.expired-reservations-cron:0 */5 * * * *}")
    public void releaseExpiredReservations() {
        log.info("Starting scheduled task: Release expired reservations");

        releaseExpiredTimer.record(() -> {
            try {
                int releasedCount = inventoryService.releaseExpiredReservations();

                if (releasedCount > 0) {
                    expiredReservationsCounter.increment(releasedCount);
                    log.info("Successfully released {} expired reservations", releasedCount);
                }
            } catch (Exception e) {
                log.error("Error in scheduled task - release expired reservations: {}", e.getMessage(), e);
            }
        });

        log.info("Completed scheduled task: Release expired reservations");
    }

    /**
     * Cleanup old reservation records
     * Runs daily at 2 AM by default
     */
    @Scheduled(cron = "${inventory.scheduled.cleanup-old-reservations-cron:0 0 2 * * *}")
    public void cleanupOldReservations() {
        log.info("Starting scheduled task: Cleanup old reservations (older than {} days)", cleanupDaysThreshold);

        cleanupOldTimer.record(() -> {
            try {
                LocalDateTime cutoffDate = LocalDateTime.now().minusDays(cleanupDaysThreshold);
                long countBefore = reservationRepository.count();

                reservationRepository.deleteByCreatedAtBefore(cutoffDate);

                long countAfter = reservationRepository.count();
                long deletedCount = countBefore - countAfter;

                if (deletedCount > 0) {
                    cleanedReservationsCounter.increment(deletedCount);
                    log.info("Successfully cleaned up {} old reservation records", deletedCount);
                }
            } catch (Exception e) {
                log.error("Error in scheduled task - cleanup old reservations: {}", e.getMessage(), e);
            }
        });

        log.info("Completed scheduled task: Cleanup old reservations");
    }

    @Scheduled(cron = "${inventory.scheduled.restock-alert-cron:0 0 6 * * ?}")
    public void generateRestockAlerts() {
        log.info("Starting scheduled task: Generate restock alerts");

        restockAlertTimer.record(() -> {
            try {
                List<Inventory> itemsNeedingReorder = inventoryService.getItemsNeedingReorder();

                lowStockItemsCount.set(itemsNeedingReorder.size());

                if (!itemsNeedingReorder.isEmpty()) {
                    log.warn("=== RESTOCK ALERT ===");
                    log.warn("Found {} items needing reorder:", itemsNeedingReorder.size());

                    for (Inventory item : itemsNeedingReorder) {
                        log.warn("  Product: {} (SKU: {})", item.getProductId(), item.getSku());
                        log.warn("    Current Stock: {}, Reserved: {}, Available: {}",
                                item.getQuantity(), item.getReservedQuantity(), item.getAvailableQuantity());
                        log.warn("    Reorder Level: {}, Suggested Reorder Qty: {}",
                                item.getReorderLevel(), item.getReorderQuantity());
                    }

                    log.warn("======================");

                    restockAlertsCounter.increment(itemsNeedingReorder.size());

                    // In a full implementation, this would:
                    // 1. Send notifications via email/Slack
                    // 2. Create purchase order requests
                    // 3. Publish events to notification service

                } else {
                    log.info("No items need restocking");
                }

            } catch (Exception e) {
                log.error("Error generating restock alerts: {}", e.getMessage(), e);
            }
        });

        log.info("Completed scheduled task: Generate restock alerts");
    }
}
