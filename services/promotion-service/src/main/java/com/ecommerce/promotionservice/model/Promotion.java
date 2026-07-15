package com.ecommerce.promotionservice.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "promotions", indexes = {
    @Index(name = "idx_code", columnList = "code", unique = true),
    @Index(name = "idx_active_dates", columnList = "active, startDate, endDate"),
    @Index(name = "idx_type", columnList = "type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Promotion implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic-lock guard. Concurrent redemptions of a limited-use code each
     * read then increment {@code currentUses}; the version check makes the
     * increment atomic so two writers cannot both commit off the same read and
     * push usage past {@code maxUses}. Managed by Hibernate.
     */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(nullable = false, unique = true, length = 50)
    @NotBlank(message = "Promotion code is required")
    @Size(min = 3, max = 50, message = "Code must be between 3 and 50 characters")
    @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Code must contain only uppercase letters, numbers, hyphens and underscores")
    private String code;

    @Column(nullable = false, length = 100)
    @NotBlank(message = "Promotion name is required")
    @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
    private String name;

    @Column(length = 500)
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @NotNull(message = "Promotion type is required")
    private PromotionType type;

    @Column(nullable = false, precision = 10, scale = 2)
    @NotNull(message = "Discount value is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Discount value must be greater than 0")
    private BigDecimal discountValue;

    @Column(precision = 10, scale = 2)
    @DecimalMin(value = "0.0", message = "Minimum purchase amount must be non-negative")
    private BigDecimal minPurchaseAmount;

    @Column
    @Min(value = 1, message = "Maximum uses must be at least 1")
    private Integer maxUses;

    @Column(nullable = false)
    @Builder.Default
    private Integer currentUses = 0;

    @Column(nullable = false)
    @NotNull(message = "Start date is required")
    private LocalDateTime startDate;

    @Column(nullable = false)
    @NotNull(message = "End date is required")
    private LocalDateTime endDate;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "promotion_categories", joinColumns = @JoinColumn(name = "promotion_id"))
    @Column(name = "category_id")
    @Builder.Default
    private Set<Long> applicableCategories = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "promotion_products", joinColumns = @JoinColumn(name = "promotion_id"))
    @Column(name = "product_id")
    @Builder.Default
    private Set<Long> applicableProducts = new HashSet<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void validate() {
        if (endDate != null && startDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }

        if (type == PromotionType.PERCENTAGE && discountValue != null && discountValue.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Percentage discount cannot exceed 100");
        }
    }

    public boolean isValid() {
        LocalDateTime now = LocalDateTime.now();
        return active &&
               now.isAfter(startDate) &&
               now.isBefore(endDate) &&
               (maxUses == null || currentUses < maxUses);
    }

    public boolean isApplicableToCategory(Long categoryId) {
        return applicableCategories.isEmpty() || applicableCategories.contains(categoryId);
    }

    public boolean isApplicableToProduct(Long productId) {
        return applicableProducts.contains(productId);
    }

    public void incrementUsage() {
        if (maxUses != null && currentUses >= maxUses) {
            throw new IllegalStateException("Promotion has reached maximum usage limit");
        }
        this.currentUses++;
    }
}
