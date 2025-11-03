package com.ecommerce.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * Event published when a payment fails.
 * Triggers order cancellation, inventory release, and customer notification.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PaymentFailedEvent extends BaseEvent {

    private static final long serialVersionUID = 1L;

    /**
     * Payment identifier
     */
    private String paymentId;

    /**
     * Order identifier
     */
    private String orderId;

    /**
     * Customer identifier
     */
    private String customerId;

    /**
     * Payment amount attempted
     */
    private BigDecimal amount;

    /**
     * Currency code
     */
    private String currency;

    /**
     * Payment method attempted
     */
    private String paymentMethod;

    /**
     * Payment intent ID (from payment gateway)
     */
    private String paymentIntentId;

    /**
     * Failure reason code (INSUFFICIENT_FUNDS, CARD_DECLINED, EXPIRED_CARD, etc.)
     */
    private String failureCode;

    /**
     * Human-readable failure message
     */
    private String failureMessage;

    /**
     * Whether the payment can be retried
     */
    private Boolean retryable;

    /**
     * Payment gateway error code
     */
    private String gatewayErrorCode;

    @Override
    public String getPartitionKey() {
        return orderId;
    }
}
