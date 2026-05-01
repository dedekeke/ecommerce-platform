package com.ecommerce.promotionservice.loyalty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Per-user lifetime spend, accumulated by {@link OrderCompletedConsumer} as
 * {@code order.completed} events arrive.
 *
 * <p>The primary key is the userId (Auth0 sub) — this is the shape promotion
 * service uses everywhere else for cross-service IDs and is cheap to look up.</p>
 */
@Entity
@Table(name = "customer_spend")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerSpend {

    @Id
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "total_spend", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalSpend = BigDecimal.ZERO;

    @Column(name = "last_order_at")
    private LocalDateTime lastOrderAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
