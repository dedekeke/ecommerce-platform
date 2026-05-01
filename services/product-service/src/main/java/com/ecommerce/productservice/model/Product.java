package com.ecommerce.productservice.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Product entity representing a product in the catalog.
 *
 * Optimized for read-heavy workload with proper indexing on:
 * - name (for search)
 * - SKU (unique identifier)
 * - category (for filtering)
 * - active status (for filtering)
 */
@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_product_sku", columnList = "sku", unique = true),
    @Index(name = "idx_product_name", columnList = "name"),
    @Index(name = "idx_product_category", columnList = "category_id"),
    @Index(name = "idx_product_active", columnList = "active"),
    @Index(name = "idx_product_price", columnList = "price"),
    // V3__Add_perf_indexes — see docs/DB_INDEX_AUDIT.md
    @Index(name = "idx_product_active_category", columnList = "active, category_id"),
    @Index(name = "idx_product_active_created",  columnList = "active, created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "SKU is required")
    @Size(max = 50, message = "SKU must not exceed 50 characters")
    @Column(nullable = false, unique = true, length = 50)
    private String sku;

    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name must not exceed 255 characters")
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @NotBlank(message = "Currency is required")
    @Size(max = 3, message = "Currency code must be 3 characters")
    @Column(nullable = false, length = 3)
    private String currency;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "image_url", length = 500)
    @Builder.Default
    private Set<String> images = new HashSet<>();

    @Embedded
    private Dimensions dimensions;

    @Min(value = 0, message = "Stock quantity cannot be negative")
    @Column(nullable = false)
    @Builder.Default
    private Integer stockQuantity = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * Checks if the product is in stock.
     */
    public boolean isInStock() {
        return stockQuantity != null && stockQuantity > 0;
    }

    /**
     * Checks if the product is available for purchase.
     */
    public boolean isAvailable() {
        return active != null && active && isInStock();
    }
}
