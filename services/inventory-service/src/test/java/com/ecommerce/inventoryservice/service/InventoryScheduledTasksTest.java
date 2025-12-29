package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.repository.InventoryReservationRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Inventory Scheduled Tasks Tests")
class InventoryScheduledTasksTest {

    @Mock
    private InventoryService inventoryService;

    @Mock
    private InventoryReservationRepository reservationRepository;

    private InventoryScheduledTasks scheduledTasks;

    private static final int CLEANUP_DAYS_THRESHOLD = 90;

    @BeforeEach
    void setUp() {
        scheduledTasks = new InventoryScheduledTasks(
                inventoryService,
                reservationRepository,
                new SimpleMeterRegistry(),
                CLEANUP_DAYS_THRESHOLD
        );
    }

    @Nested
    @DisplayName("Release Expired Reservations Tests")
    class ReleaseExpiredReservationsTests {

        @Test
        @DisplayName("Should release expired reservations when found")
        void shouldReleaseExpiredReservationsWhenFound() {
            // Given
            when(inventoryService.releaseExpiredReservations()).thenReturn(5);

            // When
            scheduledTasks.releaseExpiredReservations();

            // Then
            verify(inventoryService).releaseExpiredReservations();
        }

        @Test
        @DisplayName("Should handle no expired reservations gracefully")
        void shouldHandleNoExpiredReservationsGracefully() {
            // Given
            when(inventoryService.releaseExpiredReservations()).thenReturn(0);

            // When
            scheduledTasks.releaseExpiredReservations();

            // Then
            verify(inventoryService).releaseExpiredReservations();
        }

        @Test
        @DisplayName("Should handle exception gracefully during release")
        void shouldHandleExceptionGracefully() {
            // Given
            when(inventoryService.releaseExpiredReservations())
                    .thenThrow(new RuntimeException("Database error"));

            // When - should not throw
            scheduledTasks.releaseExpiredReservations();

            // Then
            verify(inventoryService).releaseExpiredReservations();
        }
    }

    @Nested
    @DisplayName("Cleanup Old Reservations Tests")
    class CleanupOldReservationsTests {

        @Test
        @DisplayName("Should cleanup old reservations when found")
        void shouldCleanupOldReservationsWhenFound() {
            // Given
            when(reservationRepository.count()).thenReturn(100L, 90L);

            // When
            scheduledTasks.cleanupOldReservations();

            // Then
            verify(reservationRepository).deleteByCreatedAtBefore(any());
            verify(reservationRepository, times(2)).count();
        }

        @Test
        @DisplayName("Should handle no old reservations gracefully")
        void shouldHandleNoOldReservationsGracefully() {
            // Given
            when(reservationRepository.count()).thenReturn(100L, 100L);

            // When
            scheduledTasks.cleanupOldReservations();

            // Then
            verify(reservationRepository).deleteByCreatedAtBefore(any());
        }

        @Test
        @DisplayName("Should handle exception gracefully during cleanup")
        void shouldHandleExceptionGracefully() {
            // Given
            when(reservationRepository.count())
                    .thenThrow(new RuntimeException("Database error"));

            // When - should not throw
            scheduledTasks.cleanupOldReservations();

            // Then
            verify(reservationRepository).count();
        }
    }

    @Nested
    @DisplayName("Generate Restock Alerts Tests")
    class GenerateRestockAlertsTests {

        @Test
        @DisplayName("Should generate restock alerts when items need reorder")
        void shouldGenerateAlertsWhenItemsNeedReorder() {
            // Given
            com.ecommerce.inventoryservice.domain.entity.Inventory item1 =
                    com.ecommerce.inventoryservice.domain.entity.Inventory.builder()
                            .productId("prod-1")
                            .sku("SKU-001")
                            .quantity(5)
                            .reservedQuantity(0)
                            .reorderLevel(10)
                            .reorderQuantity(50)
                            .build();

            when(inventoryService.getItemsNeedingReorder())
                    .thenReturn(java.util.Collections.singletonList(item1));

            // When
            scheduledTasks.generateRestockAlerts();

            // Then
            verify(inventoryService).getItemsNeedingReorder();
        }

        @Test
        @DisplayName("Should handle no items needing reorder gracefully")
        void shouldHandleNoItemsNeedingReorder() {
            // Given
            when(inventoryService.getItemsNeedingReorder())
                    .thenReturn(java.util.Collections.emptyList());

            // When
            scheduledTasks.generateRestockAlerts();

            // Then
            verify(inventoryService).getItemsNeedingReorder();
        }

        @Test
        @DisplayName("Should handle exception gracefully during restock alert generation")
        void shouldHandleExceptionGracefully() {
            // Given
            when(inventoryService.getItemsNeedingReorder())
                    .thenThrow(new RuntimeException("Database error"));

            // When - should not throw
            scheduledTasks.generateRestockAlerts();

            // Then
            verify(inventoryService).getItemsNeedingReorder();
        }
    }
}
