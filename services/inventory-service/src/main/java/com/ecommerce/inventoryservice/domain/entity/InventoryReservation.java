package com.ecommerce.inventoryservice.domain.entity;

import com.ecommerce.inventoryservice.domain.enums.ReservationStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inventory_reservations", indexes = {
    @Index(name = "idx_reservation_product_id", columnList = "productId"),
    @Index(name = "idx_reservation_order_id", columnList = "orderId"),
    @Index(name = "idx_reservation_status", columnList = "status"),
    @Index(name = "idx_reservation_expires_at", columnList = "expiresAt"),
    // V2__Add_perf_indexes — see docs/DB_INDEX_AUDIT.md.
    // The partial-index variant (WHERE status = 'RESERVED') is created in V2 SQL —
    // JPA cannot express partial indexes, so the @Index here is a non-partial
    // composite that still helps when the V2 SQL has not yet been applied.
    @Index(name = "idx_reservation_status_expires", columnList = "status, expiresAt")
})
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank(message = "Product ID is required")
    @Column(nullable = false)
    private String productId;

    @NotBlank(message = "Order ID is required")
    @Column(nullable = false)
    private String orderId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.RESERVED;

    @NotNull(message = "Expiration time is required")
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Check if reservation has expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt) && status == ReservationStatus.RESERVED;
    }

    /**
     * Check if reservation can be committed
     */
    public boolean canBeCommitted() {
        return status == ReservationStatus.RESERVED && !isExpired();
    }

    /**
     * Check if reservation can be released
     */
    public boolean canBeReleased() {
        return status == ReservationStatus.RESERVED;
    }

    /**
     * Mark reservation as committed
     */
    public void commit() {
        if (!canBeCommitted()) {
            throw new IllegalStateException("Cannot commit reservation in status: " + status);
        }
        this.status = ReservationStatus.COMMITTED;
    }

    /**
     * Mark reservation as released
     */
    public void release() {
        if (!canBeReleased()) {
            throw new IllegalStateException("Cannot release reservation in status: " + status);
        }
        this.status = ReservationStatus.RELEASED;
    }

    /**
     * Mark reservation as expired
     */
    public void expire() {
        if (status != ReservationStatus.RESERVED) {
            throw new IllegalStateException("Cannot expire reservation in status: " + status);
        }
        this.status = ReservationStatus.EXPIRED;
    }
}
