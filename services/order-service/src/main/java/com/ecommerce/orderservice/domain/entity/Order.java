package com.ecommerce.orderservice.domain.entity;

import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_order_number", columnList = "orderNumber", unique = true),
    @Index(name = "idx_user_id", columnList = "userId"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_promotion_code", columnList = "promotionCode"),
    @Index(name = "idx_user_status_date", columnList = "userId, status, createdAt"),
    // V2__Add_perf_indexes — see docs/DB_INDEX_AUDIT.md
    @Index(name = "idx_order_payment_intent", columnList = "paymentIntentId"),
    @Index(name = "idx_order_status_created", columnList = "status, createdAt")
})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank(message = "Order number is required")
    @Column(nullable = false, unique = true)
    private String orderNumber;

    @NotBlank(message = "User ID is required")
    @Column(nullable = false)
    private String userId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @NotNull(message = "Subtotal is required")
    @DecimalMin(value = "0.0", message = "Subtotal must be non-negative")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @NotNull(message = "Tax is required")
    @DecimalMin(value = "0.0", message = "Tax must be non-negative")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal tax;

    @NotNull(message = "Shipping cost is required")
    @DecimalMin(value = "0.0", message = "Shipping cost must be non-negative")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal shippingCost;

    @NotNull(message = "Total is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Total must be greater than 0")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Embedded
    @NotNull(message = "Shipping address is required")
    private Address shippingAddress;

    private String paymentIntentId;

    private String promotionCode;

    @Column(precision = 10, scale = 2)
    private BigDecimal discountAmount;

    /**
     * Loyalty tier discount applied on top of any promotion-code discount.
     * Computed at checkout from the customer's promotion-service tier and
     * surfaced here so the pricing breakdown is auditable.
     */
    @Column(name = "loyalty_discount", precision = 10, scale = 2)
    private BigDecimal loyaltyDiscount;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
    }

    @PrePersist
    @PreUpdate
    public void calculateTotals() {
        // First, ensure all items have their subtotals calculated
        items.forEach(item -> {
            if (item.getSubtotal() == null) {
                item.calculateSubtotal();
            }
        });

        // Calculate subtotal from items
        this.subtotal = items.stream()
                .map(item -> item.getSubtotal() != null ? item.getSubtotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Apply promotion-code discount, then loyalty-tier discount. Both are
        // stored as absolute amounts; loyalty is computed from the
        // post-promotion subtotal upstream (see OrderService.createOrder).
        BigDecimal discountedSubtotal = subtotal;
        if (discountAmount != null && discountAmount.compareTo(BigDecimal.ZERO) > 0) {
            discountedSubtotal = discountedSubtotal.subtract(discountAmount);
        }
        if (loyaltyDiscount != null && loyaltyDiscount.compareTo(BigDecimal.ZERO) > 0) {
            discountedSubtotal = discountedSubtotal.subtract(loyaltyDiscount);
        }
        if (discountedSubtotal.compareTo(BigDecimal.ZERO) < 0) {
            discountedSubtotal = BigDecimal.ZERO;
        }

        // Calculate total (ensure tax and shippingCost are not null)
        BigDecimal taxValue = tax != null ? tax : BigDecimal.ZERO;
        BigDecimal shippingValue = shippingCost != null ? shippingCost : BigDecimal.ZERO;
        this.total = discountedSubtotal.add(taxValue).add(shippingValue);
    }


    public void updateStatus(OrderStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                String.format("Cannot transition from %s to %s", this.status, newStatus)
            );
        }
        this.status = newStatus;
    }

    public boolean canBeCancelled() {
        return this.status.isCancellable();
    }

    public boolean canBeRefunded() {
        return this.status.isRefundable();
    }
}
