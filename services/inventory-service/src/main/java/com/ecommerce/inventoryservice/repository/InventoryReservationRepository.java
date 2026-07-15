package com.ecommerce.inventoryservice.repository;

import com.ecommerce.inventoryservice.domain.entity.InventoryReservation;
import com.ecommerce.inventoryservice.domain.enums.ReservationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, String> {

    /**
     * Find reservation by order ID
     */
    List<InventoryReservation> findByOrderId(String orderId);

    /**
     * Find reservation by order ID and product ID
     */
    Optional<InventoryReservation> findByOrderIdAndProductId(String orderId, String productId);

    /**
     * Find reservations by product ID
     */
    List<InventoryReservation> findByProductId(String productId);

    /**
     * Find reservations by status
     */
    List<InventoryReservation> findByStatus(ReservationStatus status);

    /**
     * Find expired reservations that are still in RESERVED status.
     *
     * <p>Paged deliberately: the release job must never load an unbounded
     * backlog into the heap if it falls behind. Callers iterate in fixed-size
     * batches (see {@code InventoryService#releaseExpiredReservations}).
     */
    @Query("SELECT r FROM InventoryReservation r WHERE r.status = 'RESERVED' AND r.expiresAt < :currentTime ORDER BY r.expiresAt ASC")
    List<InventoryReservation> findExpiredReservations(@Param("currentTime") LocalDateTime currentTime, Pageable pageable);

    /**
     * Find active reservations for a product
     */
    @Query("SELECT r FROM InventoryReservation r WHERE r.productId = :productId AND r.status = 'RESERVED' AND r.expiresAt > :currentTime")
    List<InventoryReservation> findActiveReservationsByProductId(@Param("productId") String productId, @Param("currentTime") LocalDateTime currentTime);

    /**
     * Delete reservations older than the specified date.
     *
     * @return the number of records deleted — used directly for the cleanup
     * metric to avoid a count-before/count-after race with concurrent writes.
     */
    long deleteByCreatedAtBefore(LocalDateTime date);
}
