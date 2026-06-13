package com.ecommerce.orderservice.saga.rma;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A single returned line within a {@link Return}. Models partial returns:
 * a customer may return a subset of an order's items, each with its own
 * quantity and reason. The refund saga sums the {@code approved} lines'
 * extended value ({@code unitPrice * quantity}) to compute the refund.
 *
 * <p>{@code approved} is decided at inspection time. A line defaults to
 * unapproved so the refund amount is never over-counted before inspection.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "return_lines", indexes = {
    @Index(name = "idx_return_lines_return", columnList = "return_id"),
    @Index(name = "idx_return_lines_order_item", columnList = "orderItemId")
})
public class ReturnLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_id", nullable = false)
    private Return returnRequest;

    /** The order item being returned. */
    @Column(nullable = false, length = 128)
    @NotNull
    private String orderItemId;

    @Column(length = 128)
    private String productId;

    @Column(nullable = false)
    @Min(1)
    private Integer quantity;

    /** Per-unit price snapshot, used to compute the line's refund value. */
    @Column(name = "unit_price", precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false)
    @Builder.Default
    private boolean approved = false;

    /**
     * Extended refund value of this line: {@code unitPrice * quantity}.
     * Returns {@link BigDecimal#ZERO} when price or quantity is missing.
     */
    public BigDecimal lineValue() {
        if (unitPrice == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
