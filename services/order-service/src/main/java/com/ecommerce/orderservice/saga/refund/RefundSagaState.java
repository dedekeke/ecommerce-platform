package com.ecommerce.orderservice.saga.refund;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Persistent state for a single refund-saga run. Persisting per-step progress
 * is what lets the recovery scheduler resume sagas after a JVM crash without
 * replaying already-successful steps.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "refund_saga_state", indexes = {
    @Index(name = "idx_refund_saga_order_id", columnList = "orderId"),
    @Index(name = "idx_refund_saga_status", columnList = "status"),
    @Index(name = "idx_refund_saga_updated_at", columnList = "updatedAt")
})
public class RefundSagaState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String orderId;

    @Column
    private String userId;

    @Column
    private String reason;

    @Column(precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Column
    private String paymentIntentId;

    @Column
    private String refundTransactionId;

    @Column
    private String restorationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RefundSagaStatus status = RefundSagaStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RefundSagaStep currentStep = RefundSagaStep.VALIDATE;

    @ElementCollection(targetClass = RefundSagaStep.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "refund_saga_completed_steps",
        joinColumns = @JoinColumn(name = "saga_id"))
    @Column(name = "step")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<RefundSagaStep> completedSteps = EnumSet.noneOf(RefundSagaStep.class);

    @Column(length = 1024)
    private String failureReason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Mark a step as completed. Idempotent — repeated calls are no-ops.
     */
    public void markStepCompleted(RefundSagaStep step) {
        if (completedSteps == null) {
            completedSteps = EnumSet.noneOf(RefundSagaStep.class);
        }
        completedSteps.add(step);
    }

    public boolean hasCompleted(RefundSagaStep step) {
        return completedSteps != null && completedSteps.contains(step);
    }

    /**
     * Returns completed steps in declared (forward) order.
     */
    public List<RefundSagaStep> completedStepsInOrder() {
        List<RefundSagaStep> ordered = new ArrayList<>();
        for (RefundSagaStep step : RefundSagaStep.values()) {
            if (hasCompleted(step)) {
                ordered.add(step);
            }
        }
        return ordered;
    }
}
