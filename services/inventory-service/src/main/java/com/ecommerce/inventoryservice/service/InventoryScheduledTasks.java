package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryScheduledTasks {

    private final InventoryService inventoryService;
    private final InventoryReservationRepository reservationRepository;

    @Value("${inventory.scheduled.cleanup-days-threshold:90}")
    private int cleanupDaysThreshold;

    /**
     * Auto-release expired reservations
     * Runs every 5 minutes by default
     */
    @Scheduled(cron = "${inventory.scheduled.expired-reservations-cron:0 */5 * * * *}")
    public void releaseExpiredReservations() {
        log.info("Starting scheduled task: Release expired reservations");

        try {
            int releasedCount = inventoryService.releaseExpiredReservations();

            if (releasedCount > 0) {
                log.info("Successfully released {} expired reservations", releasedCount);
            }
        } catch (Exception e) {
            log.error("Error in scheduled task - release expired reservations: {}", e.getMessage(), e);
        }
    }

    /**
     * Cleanup old reservation records
     * Runs daily at 2 AM by default
     */
    @Scheduled(cron = "${inventory.scheduled.cleanup-old-reservations-cron:0 0 2 * * *}")
    public void cleanupOldReservations() {
        log.info("Starting scheduled task: Cleanup old reservations (older than {} days)", cleanupDaysThreshold);

        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(cleanupDaysThreshold);
            long countBefore = reservationRepository.count();

            reservationRepository.deleteByCreatedAtBefore(cutoffDate);

            long countAfter = reservationRepository.count();
            long deletedCount = countBefore - countAfter;

            if (deletedCount > 0) {
                log.info("Successfully cleaned up {} old reservation records", deletedCount);
            }
        } catch (Exception e) {
            log.error("Error in scheduled task - cleanup old reservations: {}", e.getMessage(), e);
        }
    }
}
