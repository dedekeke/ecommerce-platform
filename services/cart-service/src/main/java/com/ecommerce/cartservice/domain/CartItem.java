package com.ecommerce.cartservice.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * CartItem Entity
 *
 * Represents an item in a shopping cart.
 *
 * Key Features:
 * - Product association via productId
 * - Quantity management
 * - Price snapshot (captured at time of adding to cart)
 * - Subtotal calculation
 */
@Entity
@Table(name = "cart_items", indexes = {
    @Index(name = "idx_cart_id", columnList = "cart_id"),
    @Index(name = "idx_product_id", columnList = "product_id")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Parent cart
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    @NotNull(message = "Cart is required")
    private Cart cart;

    /**
     * Product ID (from Product service)
     * Not a foreign key as Product service is separate
     */
    @Column(name = "product_id", nullable = false)
    @NotNull(message = "Product ID is required")
    private String productId;

    /**
     * Product name (snapshot for display)
     */
    @Column(name = "product_name", nullable = false, length = 255)
    @NotNull(message = "Product name is required")
    private String productName;

    /**
     * Product SKU (snapshot)
     */
    @Column(name = "product_sku", length = 100)
    private String productSku;

    /**
     * Product image URL (snapshot)
     */
    @Column(name = "product_image_url", length = 500)
    private String productImageUrl;

    /**
     * Price snapshot (price at time of adding to cart)
     * This prevents price changes from affecting cart until checkout
     */
    @Column(name = "price_snapshot", nullable = false, precision = 10, scale = 2)
    @NotNull(message = "Price is required")
    @Min(value = 0, message = "Price must be positive")
    private BigDecimal priceSnapshot;

    /**
     * Quantity of this product in cart
     */
    @Column(nullable = false)
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    /**
     * Subtotal (price * quantity)
     * Calculated field
     */
    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Calculate subtotal based on price and quantity
     */
    public void calculateSubtotal() {
        if (priceSnapshot != null && quantity != null) {
            this.subtotal = priceSnapshot.multiply(BigDecimal.valueOf(quantity));
        }
    }

    /**
     * Update quantity and recalculate subtotal
     */
    public void updateQuantity(Integer newQuantity) {
        this.quantity = newQuantity;
        calculateSubtotal();
        if (cart != null) {
            cart.recalculateTotals();
        }
    }

    /**
     * Increment quantity by 1
     */
    public void incrementQuantity() {
        updateQuantity(this.quantity + 1);
    }

    /**
     * Decrement quantity by 1
     */
    public void decrementQuantity() {
        if (this.quantity > 1) {
            updateQuantity(this.quantity - 1);
        }
    }

    /**
     * Pre-persist and pre-update hook to calculate subtotal
     */
    @PrePersist
    @PreUpdate
    private void prePersistOrUpdate() {
        calculateSubtotal();
    }
}
