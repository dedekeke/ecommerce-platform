package com.ecommerce.orderservice.saga.rma;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

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
}
