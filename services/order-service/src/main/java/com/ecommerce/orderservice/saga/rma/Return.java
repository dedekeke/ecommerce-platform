package com.ecommerce.orderservice.saga.rma;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
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

/**
 * JPA entity backing a single RMA saga row.
 *
 * <p>Mirrors the {@code returns} Flyway table (V6). One row per return
 * request — even if the same order is returned twice (rare), the
 * {@code rmaNumber} differs.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "returns", indexes = {
    @Index(name = "idx_returns_order", columnList = "orderId"),
    @Index(name = "idx_returns_user", columnList = "userId"),
    @Index(name = "idx_returns_status", columnList = "status"),
    @Index(name = "idx_returns_rma_number", columnList = "rmaNumber", unique = true)
})
public class Return {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true, length = 64)
    private String rmaNumber;

    @Column(nullable = false, length = 128)
    private String orderId;

    @Column(nullable = false, length = 128)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private ReturnStatus status = ReturnStatus.REQUESTED;

    @Column(length = 512)
    private String returnLabelUrl;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime receivedAt;
    private LocalDateTime inspectedAt;

    /** APPROVED / REJECTED — set during inspect(). */
    @Column(length = 32)
    private String outcome;

    /**
     * Restocking fee percentage (0..100) recorded at inspection. The refund
     * saga deducts this from the approved line total. Null means no fee.
     */
    @Column(name = "restocking_fee_percent", precision = 5, scale = 2)
    private BigDecimal restockingFeePercent;

    /**
     * Line-item-level returns. A whole-order return simply has one line per
     * order item; partial returns carry the returned subset.
     */
    @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL,
        orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<ReturnLine> lines = new ArrayList<>();

    /** Free-text condition (NEW, OPENED, DAMAGED…). */
    @Column(length = 64)
    private String condition;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Set after approve path triggers RefundOrchestrator. */
    @Column(length = 64)
    private String refundSagaId;

    @Column(columnDefinition = "TEXT")
    private String failureReason;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public void addLine(ReturnLine line) {
        if (lines == null) {
            lines = new ArrayList<>();
        }
        line.setReturnRequest(this);
        lines.add(line);
    }

    /**
     * Sum of the extended value of every {@code approved} line. This is the
     * pre-restocking-fee refund base for a partial return.
     */
    public BigDecimal approvedLinesTotal() {
        if (lines == null || lines.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return lines.stream()
            .filter(ReturnLine::isApproved)
            .map(ReturnLine::lineValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
