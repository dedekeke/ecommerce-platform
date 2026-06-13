package com.ecommerce.orderservice.saga.refund;

import com.ecommerce.orderservice.domain.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Mutable context passed through every step of a single refund-saga run.
 *
 * <p>Steps read fields they need (e.g. {@code paymentIntentId}) and write
 * outputs back (e.g. {@code refundTransactionId}). Keeping the data outside
 * {@link RefundSagaState} means we don't churn the DB row on every step's
 * intermediate scratch values — only what survives a crash gets persisted.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundSagaContext {

    private RefundSagaState state;
    private String orderId;
    private String reason;

    private Order order;
    private String paymentIntentId;
    private BigDecimal refundAmount;

    /**
     * Optional pre-computed refund base. When set (e.g. by an RMA partial
     * return summing approved lines), the validate step uses this instead of
     * the full order total. Restocking fee is applied on top.
     */
    private BigDecimal refundAmountOverride;

    /** Restocking fee percentage (0..100) to deduct, or null for none. */
    private BigDecimal restockingFeePercent;

    private String refundTransactionId;
    private String restorationId;

    private String userId;
    private String userEmail;
    private String orderNumber;

    public RefundSagaStep currentStep() {
        return state == null ? null : state.getCurrentStep();
    }
}
