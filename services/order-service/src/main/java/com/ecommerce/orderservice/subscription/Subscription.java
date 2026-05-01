package com.ecommerce.orderservice.subscription;

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

/**
 * JPA entity for §3.6 subscription / recurring orders.
 *
 * <p>The {@code shippingAddressJson} column stores a serialized JSON snapshot
 * of the address rather than embedding columns — this keeps the schema simple
 * and lets the Order service evolve the Address shape independently of the
 * subscription history.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "subscriptions", indexes = {
    @Index(name = "idx_subscriptions_user", columnList = "userId")
})
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 128)
    private String userId;

    @NotBlank
    @Column(nullable = false, length = 128)
    private String productId;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer quantity;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer intervalDays;

    @Column(length = 128)
    private String paymentMethodId;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String shippingAddressJson;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SubscriptionStatus status;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime nextRunAt;

    private LocalDateTime lastRunAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
