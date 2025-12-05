package com.ecommerce.inventoryservice.domain.entity;

import com.ecommerce.inventoryservice.domain.enums.InventoryStatus;
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
@Table(name = "inventory", indexes = {
    @Index(name = "idx_product_id", columnList = "productId", unique = true),
    @Index(name = "idx_sku", columnList = "sku", unique = true),
    @Index(name = "idx_status", columnList = "status")
})
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank(message = "Product ID is required")
    @Column(nullable = false, unique = true)
    private String productId;

    @NotBlank(message = "SKU is required")
    @Column(nullable = false, unique = true)
    private String sku;

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity must be non-negative")
    @Column(nullable = false)
    private Integer quantity;

    @NotNull(message = "Reserved quantity is required")
    @Min(value = 0, message = "Reserved quantity must be non-negative")
    @Column(nullable = false)
    @Builder.Default
    private Integer reservedQuantity = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InventoryStatus status = InventoryStatus.IN_STOCK;

    @NotNull(message = "Reorder level is required")
    @Min(value = 0, message = "Reorder level must be non-negative")
    @Column(nullable = false)
    private Integer reorderLevel;

    @NotNull(message = "Reorder quantity is required")
    @Min(value = 0, message = "Reorder quantity must be non-negative")
    @Column(nullable = false)
    private Integer reorderQuantity;

    @Column(name = "last_restocked_at")
    private LocalDateTime lastRestockedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Calculate available quantity (quantity - reservedQuantity)
     */
    public Integer getAvailableQuantity() {
        return quantity - reservedQuantity;
    }

    /**
     * Check if product is available in the requested quantity
     */
    public boolean isAvailable(Integer requestedQuantity) {
        return getAvailableQuantity() >= requestedQuantity;
    }

    /**
     * Reserve stock
     */
    public void reserveStock(Integer amount) {
        if (!isAvailable(amount)) {
            throw new IllegalStateException("Insufficient stock available");
        }
        this.reservedQuantity += amount;
        updateStatus();
    }

    /**
     * Release reserved stock back to available
     */
    public void releaseReservedStock(Integer amount) {
        if (this.reservedQuantity < amount) {
            throw new IllegalStateException("Cannot release more than reserved quantity");
        }
        this.reservedQuantity -= amount;
        updateStatus();
    }

    /**
     * Commit reservation (reduce actual quantity)
     */
    public void commitReservation(Integer amount) {
        if (this.reservedQuantity < amount) {
            throw new IllegalStateException("Cannot commit more than reserved quantity");
        }
        this.reservedQuantity -= amount;
        this.quantity -= amount;
        updateStatus();
    }

    /**
     * Restock inventory
     */
    public void restock(Integer amount) {
        this.quantity += amount;
        this.lastRestockedAt = LocalDateTime.now();
        updateStatus();
    }

    /**
     * Update inventory status based on current quantities
     */
    @PrePersist
    @PreUpdate
    public void updateStatus() {
        if (this.status == InventoryStatus.DISCONTINUED) {
            return;
        }

        int availableQty = getAvailableQuantity();

        if (availableQty <= 0) {
            this.status = InventoryStatus.OUT_OF_STOCK;
        } else if (availableQty <= this.reorderLevel) {
            this.status = InventoryStatus.LOW_STOCK;
        } else {
            this.status = InventoryStatus.IN_STOCK;
        }
    }

    /**
     * Check if inventory needs reordering
     */
    public boolean needsReorder() {
        return getAvailableQuantity() <= reorderLevel && status != InventoryStatus.DISCONTINUED;
    }
}
