package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.domain.entity.Inventory;
import com.ecommerce.inventoryservice.domain.entity.InventoryReservation;
import com.ecommerce.inventoryservice.domain.enums.InventoryStatus;
import com.ecommerce.inventoryservice.domain.enums.ReservationStatus;
import com.ecommerce.inventoryservice.event.InventoryUpdatedEvent;
import com.ecommerce.inventoryservice.event.StockLowEvent;
import com.ecommerce.inventoryservice.exception.InsufficientStockException;
import com.ecommerce.inventoryservice.exception.InventoryNotFoundException;
import com.ecommerce.inventoryservice.exception.InvalidReservationException;
import com.ecommerce.inventoryservice.exception.ReservationNotFoundException;
import com.ecommerce.inventoryservice.repository.InventoryRepository;
import com.ecommerce.inventoryservice.repository.InventoryReservationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${inventory.reservation.default-expiration-minutes:15}")
    private int defaultExpirationMinutes;

    private static final String INVENTORY_UPDATED_TOPIC = "inventory-updated";
    private static final String STOCK_LOW_TOPIC = "stock-low";

    /**
     * Create or update inventory for a product
     */
    @Transactional
    public Inventory createOrUpdateInventory(Inventory inventory) {
        log.info("Creating/updating inventory for product: {}", inventory.getProductId());

        Inventory saved = inventoryRepository.save(inventory);

        publishInventoryUpdatedEvent(saved, "CREATE_OR_UPDATE", "Inventory created or updated");

        if (saved.needsReorder()) {
            publishStockLowEvent(saved);
        }

        return saved;
    }

    /**
     * Get inventory by product ID
     */
    public Inventory getInventoryByProductId(String productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found for product: " + productId));
    }

    /**
     * Get inventory by SKU
     */
    public Inventory getInventoryBySku(String sku) {
        return inventoryRepository.findBySku(sku)
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found for SKU: " + sku));
    }

    /**
     * Check if product is available in requested quantity
     */
    public boolean checkAvailability(String productId, Integer quantity) {
        Inventory inventory = getInventoryByProductId(productId);
        return inventory.isAvailable(quantity);
    }

    /**
     * Bulk check availability for multiple products
     */
    public Map<String, Boolean> bulkCheckAvailability(Map<String, Integer> productQuantities) {
        return productQuantities.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> checkAvailability(entry.getKey(), entry.getValue())
                ));
    }

    /**
     * Reserve stock for an order with pessimistic locking
     */
    @Transactional
    public InventoryReservation reserveStock(String orderId, String productId, Integer quantity, Integer expirationMinutes) {
        log.info("Reserving {} units of product {} for order {}", quantity, productId, orderId);

        // Use pessimistic locking to prevent concurrent modifications
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found for product: " + productId));

        if (!inventory.isAvailable(quantity)) {
            throw new InsufficientStockException(
                    String.format("Insufficient stock for product %s. Requested: %d, Available: %d",
                            productId, quantity, inventory.getAvailableQuantity())
            );
        }

        // Reserve the stock
        inventory.reserveStock(quantity);
        inventoryRepository.save(inventory);

        // Create reservation record
        int expirationMins = expirationMinutes != null ? expirationMinutes : defaultExpirationMinutes;
        InventoryReservation reservation = InventoryReservation.builder()
                .productId(productId)
                .orderId(orderId)
                .quantity(quantity)
                .status(ReservationStatus.RESERVED)
                .expiresAt(LocalDateTime.now().plusMinutes(expirationMins))
                .build();

        InventoryReservation saved = reservationRepository.save(reservation);

        publishInventoryUpdatedEvent(inventory, "RESERVE", "Stock reserved for order: " + orderId);

        log.info("Stock reserved successfully. Reservation ID: {}", saved.getId());
        return saved;
    }

    /**
     * Reserve stock for multiple products (bulk reservation)
     */
    @Transactional
    public List<InventoryReservation> bulkReserveStock(String orderId, Map<String, Integer> productQuantities, Integer expirationMinutes) {
        return productQuantities.entrySet().stream()
                .map(entry -> reserveStock(orderId, entry.getKey(), entry.getValue(), expirationMinutes))
                .collect(Collectors.toList());
    }

    /**
     * Commit reservation (finalize the sale)
     */
    @Transactional
    public void commitReservation(String reservationId) {
        log.info("Committing reservation: {}", reservationId);

        InventoryReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException("Reservation not found: " + reservationId));

        if (!reservation.canBeCommitted()) {
            throw new InvalidReservationException(
                    String.format("Cannot commit reservation %s in status %s", reservationId, reservation.getStatus())
            );
        }

        Inventory inventory = inventoryRepository.findByProductIdForUpdate(reservation.getProductId())
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found for product: " + reservation.getProductId()));

        // Commit the reservation (reduce actual quantity)
        inventory.commitReservation(reservation.getQuantity());
        reservation.commit();

        inventoryRepository.save(inventory);
        reservationRepository.save(reservation);

        publishInventoryUpdatedEvent(inventory, "COMMIT", "Reservation committed: " + reservationId);

        if (inventory.needsReorder()) {
            publishStockLowEvent(inventory);
        }

        log.info("Reservation committed successfully");
    }

    /**
     * Commit reservations by order ID
     */
    @Transactional
    public void commitReservationsByOrderId(String orderId) {
        log.info("Committing all reservations for order: {}", orderId);

        List<InventoryReservation> reservations = reservationRepository.findByOrderId(orderId);

        if (reservations.isEmpty()) {
            throw new ReservationNotFoundException("No reservations found for order: " + orderId);
        }

        reservations.forEach(reservation -> commitReservation(reservation.getId()));
    }

    /**
     * Release reservation (cancel the order)
     */
    @Transactional
    public void releaseReservation(String reservationId) {
        log.info("Releasing reservation: {}", reservationId);

        InventoryReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException("Reservation not found: " + reservationId));

        if (!reservation.canBeReleased()) {
            throw new InvalidReservationException(
                    String.format("Cannot release reservation %s in status %s", reservationId, reservation.getStatus())
            );
        }

        Inventory inventory = inventoryRepository.findByProductIdForUpdate(reservation.getProductId())
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found for product: " + reservation.getProductId()));

        // Release the reserved stock
        inventory.releaseReservedStock(reservation.getQuantity());
        reservation.release();

        inventoryRepository.save(inventory);
        reservationRepository.save(reservation);

        publishInventoryUpdatedEvent(inventory, "RELEASE", "Reservation released: " + reservationId);

        log.info("Reservation released successfully");
    }

    /**
     * Release reservations by order ID
     */
    @Transactional
    public void releaseReservationsByOrderId(String orderId) {
        log.info("Releasing all reservations for order: {}", orderId);

        List<InventoryReservation> reservations = reservationRepository.findByOrderId(orderId);

        if (reservations.isEmpty()) {
            log.warn("No reservations found for order: {}", orderId);
            return;
        }

        reservations.forEach(reservation -> {
            if (reservation.canBeReleased()) {
                releaseReservation(reservation.getId());
            }
        });
    }

    /**
     * Update stock quantity (restock or adjustment)
     */
    @Transactional
    public Inventory updateStock(String productId, Integer quantityChange, String updateType, String notes) {
        log.info("Updating stock for product {}: {} ({})", productId, quantityChange, updateType);

        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found for product: " + productId));

        if (quantityChange > 0) {
            inventory.restock(quantityChange);
        } else if (quantityChange < 0) {
            int newQuantity = inventory.getQuantity() + quantityChange;
            if (newQuantity < inventory.getReservedQuantity()) {
                throw new InsufficientStockException("Cannot reduce quantity below reserved amount");
            }
            inventory.setQuantity(newQuantity);
        }

        Inventory saved = inventoryRepository.save(inventory);

        publishInventoryUpdatedEvent(saved, updateType, notes);

        if (saved.needsReorder()) {
            publishStockLowEvent(saved);
        }

        return saved;
    }

    /**
     * Auto-release expired reservations (scheduled job)
     */
    @Transactional
    public int releaseExpiredReservations() {
        log.info("Checking for expired reservations...");

        List<InventoryReservation> expiredReservations =
                reservationRepository.findExpiredReservations(LocalDateTime.now());

        if (expiredReservations.isEmpty()) {
            log.info("No expired reservations found");
            return 0;
        }

        log.info("Found {} expired reservations", expiredReservations.size());

        for (InventoryReservation reservation : expiredReservations) {
            try {
                Inventory inventory = inventoryRepository.findByProductIdForUpdate(reservation.getProductId())
                        .orElseThrow(() -> new InventoryNotFoundException("Inventory not found for product: " + reservation.getProductId()));

                inventory.releaseReservedStock(reservation.getQuantity());
                reservation.expire();

                inventoryRepository.save(inventory);
                reservationRepository.save(reservation);

                publishInventoryUpdatedEvent(inventory, "EXPIRE", "Reservation expired: " + reservation.getId());

                log.info("Released expired reservation: {}", reservation.getId());
            } catch (Exception e) {
                log.error("Error releasing reservation {}: {}", reservation.getId(), e.getMessage(), e);
            }
        }

        return expiredReservations.size();
    }

    /**
     * Get all inventory items needing reorder
     */
    public List<Inventory> getItemsNeedingReorder() {
        return inventoryRepository.findItemsNeedingReorder();
    }

    /**
     * Get low stock items
     */
    public List<Inventory> getLowStockItems() {
        return inventoryRepository.findByStatusIn(
                List.of(InventoryStatus.LOW_STOCK, InventoryStatus.OUT_OF_STOCK)
        );
    }

    /**
     * Publish inventory updated event to Kafka
     */
    private void publishInventoryUpdatedEvent(Inventory inventory, String updateType, String reason) {
        try {
            InventoryUpdatedEvent event = InventoryUpdatedEvent.builder()
                    .productId(inventory.getProductId())
                    .sku(inventory.getSku())
                    .quantity(inventory.getQuantity())
                    .reservedQuantity(inventory.getReservedQuantity())
                    .availableQuantity(inventory.getAvailableQuantity())
                    .status(inventory.getStatus().name())
                    .updateType(updateType)
                    .reason(reason)
                    .timestamp(LocalDateTime.now())
                    .build();

            kafkaTemplate.send(INVENTORY_UPDATED_TOPIC, inventory.getProductId(), event);
            log.debug("Published InventoryUpdatedEvent for product: {}", inventory.getProductId());
        } catch (Exception e) {
            log.error("Failed to publish InventoryUpdatedEvent: {}", e.getMessage(), e);
        }
    }

    /**
     * Publish stock low event to Kafka
     */
    private void publishStockLowEvent(Inventory inventory) {
        try {
            StockLowEvent event = StockLowEvent.builder()
                    .productId(inventory.getProductId())
                    .sku(inventory.getSku())
                    .currentQuantity(inventory.getAvailableQuantity())
                    .reorderLevel(inventory.getReorderLevel())
                    .reorderQuantity(inventory.getReorderQuantity())
                    .timestamp(LocalDateTime.now())
                    .build();

            kafkaTemplate.send(STOCK_LOW_TOPIC, inventory.getProductId(), event);
            log.info("Published StockLowEvent for product: {} (current: {}, reorder level: {})",
                    inventory.getProductId(), inventory.getAvailableQuantity(), inventory.getReorderLevel());
        } catch (Exception e) {
            log.error("Failed to publish StockLowEvent: {}", e.getMessage(), e);
        }
    }
}
