package com.ecommerce.cartservice.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Cart Entity
 *
 * Represents a shopping cart in the e-commerce platform.
 * Each user has one active cart at a time.
 *
 * Key Features:
 * - User association via userId (auth0 sub claim)
 * - Cart items management
 * - Automatic total calculation
 * - Cart expiration support
 * - Audit fields (createdAt, updatedAt)
 */
@Entity
@Table(name = "carts", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_expires_at", columnList = "expires_at")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User ID (Auth0 sub claim)
     * Not a foreign key as User service is separate
     */
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    /**
     * Cart items
     */
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    /**
     * Cart status
     */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CartStatus status = CartStatus.ACTIVE;

    /**
     * Total amount (cached for performance)
     * Recalculated whenever items change
     */
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /**
     * Total items count (cached for performance)
     */
    @Column(name = "total_items", nullable = false)
    @Builder.Default
    private Integer totalItems = 0;

    /**
     * Cart expiration timestamp
     * Carts expire after a certain period of inactivity
     */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Helper method to add an item to cart
     */
    public void addItem(CartItem item) {
        items.add(item);
        item.setCart(this);
        recalculateTotals();
    }

    /**
     * Helper method to remove an item from cart
     */
    public void removeItem(CartItem item) {
        items.remove(item);
        item.setCart(null);
        recalculateTotals();
    }

    /**
     * Recalculate total amount and total items
     */
    public void recalculateTotals() {
        this.totalAmount = items.stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.totalItems = items.stream()
                .mapToInt(CartItem::getQuantity)
                .sum();
    }

    /**
     * Check if cart is expired
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if cart is empty
     */
    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }

    /**
     * Clear all items from cart
     */
    public void clear() {
        items.clear();
        recalculateTotals();
    }

    /**
     * Mark cart as checked out
     */
    public void markAsCheckedOut() {
        this.status = CartStatus.CHECKED_OUT;
    }

    /**
     * Mark cart as abandoned
     */
    public void markAsAbandoned() {
        this.status = CartStatus.ABANDONED;
    }
}
