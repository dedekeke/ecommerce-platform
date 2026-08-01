package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.domain.entity.Inventory;
import com.ecommerce.inventoryservice.domain.entity.InventoryReservation;
import com.ecommerce.inventoryservice.domain.entity.InventoryRestoration;
import com.ecommerce.inventoryservice.domain.enums.InventoryStatus;
import com.ecommerce.inventoryservice.domain.enums.ReservationStatus;
import com.ecommerce.inventoryservice.event.InventoryStockChangedEvent;
import com.ecommerce.inventoryservice.event.InventoryUpdatedEvent;
import com.ecommerce.inventoryservice.event.StockLowEvent;
import com.ecommerce.inventoryservice.exception.InsufficientStockException;
import com.ecommerce.inventoryservice.exception.InventoryNotFoundException;
import com.ecommerce.inventoryservice.exception.InvalidReservationException;
import com.ecommerce.inventoryservice.exception.ReservationNotFoundException;
import com.ecommerce.inventoryservice.repository.InventoryRepository;
import com.ecommerce.inventoryservice.repository.InventoryReservationRepository;
import com.ecommerce.inventoryservice.repository.InventoryRestorationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryRestorationRepository restorationRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final PlatformTransactionManager transactionManager;

    /**
     * Lazily-built template that runs each expired-reservation release in its
     * OWN (REQUIRES_NEW) transaction, so one failing row cannot roll back or
     * block the rows released before/after it in the same job pass.
     */
    private TransactionTemplate requiresNewTx;

    @Value("${inventory.reservation.default-expiration-minutes:15}")
    private int defaultExpirationMinutes;

    @Value("${inventory.reservation.expired-release-batch-size:200}")
    private int expiredReleaseBatchSize;

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
     * Restore stock for refunded items. Idempotent on {@code restorationId}:
     * if the same id has been processed before, this returns {@code false}
     * without touching inventory or publishing events.
     *
     * @return {@code true} if stock was actually restored, {@code false} on
     *         a deduplicated replay.
     */
    @Transactional
    public boolean restoreStock(String restorationId,
                                String orderId,
                                String reason,
                                Map<String, Integer> productQuantities) {
        if (restorationId == null || restorationId.isBlank()) {
            throw new IllegalArgumentException("restorationId is required");
        }
        if (productQuantities == null || productQuantities.isEmpty()) {
            throw new IllegalArgumentException("productQuantities must contain at least one entry");
        }

        if (restorationRepository.existsById(restorationId)) {
            log.warn("RestoreStock replay detected for restorationId={} — skipping", restorationId);
            return false;
        }

        for (Map.Entry<String, Integer> entry : productQuantities.entrySet()) {
            String productId = entry.getKey();
            Integer qty = entry.getValue();
            if (qty == null || qty <= 0) {
                throw new IllegalArgumentException(
                        "Quantity for product " + productId + " must be positive");
            }

            Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                    .orElseThrow(() -> new InventoryNotFoundException(
                            "Inventory not found for product: " + productId));

            inventory.restock(qty);
            inventoryRepository.save(inventory);
            publishInventoryUpdatedEvent(inventory, "RESTOCK",
                    "Refund restoration: " + (reason == null ? "Customer refund" : reason));
        }

        restorationRepository.save(InventoryRestoration.builder()
                .restorationId(restorationId)
                .orderId(orderId)
                .reason(reason)
                .itemsCount(productQuantities.size())
                .build());

        log.info("RestoreStock applied: restorationId={} order={} items={}",
                restorationId, orderId, productQuantities.size());
        return true;
    }

    /**
     * Auto-release expired reservations (scheduled job).
     *
     * <p>Deliberately NOT method-level {@code @Transactional}: each reservation
     * is released in its own {@code REQUIRES_NEW} transaction
     * ({@link #releaseSingleExpiredReservation}). A single poison row therefore
     * neither rolls back the healthy rows released alongside it nor holds the
     * {@code SELECT ... FOR UPDATE} locks for the whole run.
     *
     * <p>Progress guarantee / termination: rows that fail to release are
     * recorded in {@code failedIds} and excluded from subsequent fetches. Every
     * fetched row is thus removed from the eligible set each pass — either it
     * flips to {@code EXPIRED} (released) or it is excluded (failed) — so the
     * working set strictly shrinks and the loop always terminates. This is what
     * lets healthy, later-expiring rows behind a persistently-failing head row
     * still get released instead of being head-of-line blocked forever.
     */
    public int releaseExpiredReservations() {
        log.info("Checking for expired reservations...");

        LocalDateTime now = LocalDateTime.now();
        Pageable batch = PageRequest.of(0, expiredReleaseBatchSize);
        Set<String> failedIds = new HashSet<>();
        int totalReleased = 0;

        while (true) {
            List<InventoryReservation> expired = failedIds.isEmpty()
                    ? reservationRepository.findExpiredReservations(now, batch)
                    : reservationRepository.findExpiredReservationsExcluding(now, failedIds, batch);
            if (expired.isEmpty()) {
                break;
            }

            for (InventoryReservation reservation : expired) {
                if (releaseSingleExpiredReservation(reservation)) {
                    totalReleased++;
                } else {
                    // Exclude from the next fetch so a poison row at the head of
                    // the expiresAt ordering cannot block the rows behind it.
                    failedIds.add(reservation.getId());
                }
            }

            // A partial page means we reached the tail of the eligible rows;
            // failed rows stay parked in failedIds and get retried on the next
            // scheduled run (when a transient failure may have cleared).
            if (expired.size() < expiredReleaseBatchSize) {
                break;
            }
        }

        if (totalReleased == 0) {
            log.info("No expired reservations released");
        } else {
            log.info("Released {} expired reservations", totalReleased);
        }
        if (!failedIds.isEmpty()) {
            log.warn("{} expired reservations could not be released this run; will retry next run",
                    failedIds.size());
        }
        return totalReleased;
    }

    private boolean releaseSingleExpiredReservation(InventoryReservation reservation) {
        try {
            return Boolean.TRUE.equals(requiresNewTx().execute(status -> {
                Inventory inventory = inventoryRepository.findByProductIdForUpdate(reservation.getProductId())
                        .orElseThrow(() -> new InventoryNotFoundException("Inventory not found for product: " + reservation.getProductId()));

                inventory.releaseReservedStock(reservation.getQuantity());
                reservation.expire();

                inventoryRepository.save(inventory);
                reservationRepository.save(reservation);

                publishInventoryUpdatedEvent(inventory, "EXPIRE", "Reservation expired: " + reservation.getId());

                log.info("Released expired reservation: {}", reservation.getId());
                return true;
            }));
        } catch (Exception e) {
            log.error("Error releasing reservation {}: {}", reservation.getId(), e.getMessage(), e);
            return false;
        }
    }

    private TransactionTemplate requiresNewTx() {
        if (requiresNewTx == null) {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            requiresNewTx = template;
        }
        return requiresNewTx;
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

        publishStockChangedSpringEvent(inventory, /*previousAvailable*/ null);
    }

    /**
     * In-process broadcast for the SSE fan-out. Independent of Kafka so a
     * temporarily down broker does not block real-time UI updates.
     *
     * @param previousAvailable previous available quantity if known; otherwise
     *                          the current value is reused so the payload
     *                          stays well-formed.
     */
    private void publishStockChangedSpringEvent(Inventory inventory, Integer previousAvailable) {
        if (applicationEventPublisher == null) {
            return; // Defensive: in unit tests built without the publisher.
        }
        try {
            int current = inventory.getAvailableQuantity();
            int previous = previousAvailable != null ? previousAvailable : current;
            applicationEventPublisher.publishEvent(InventoryStockChangedEvent.builder()
                    .productId(inventory.getProductId())
                    .sku(inventory.getSku())
                    .availableQty(current)
                    .previousQty(previous)
                    .timestamp(LocalDateTime.now())
                    .build());
        } catch (Exception ex) {
            log.warn("Failed to publish InventoryStockChangedEvent for product {}: {}",
                    inventory.getProductId(), ex.getMessage());
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
