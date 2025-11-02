package com.ecommerce.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * Event published when a payment is successfully completed.
 * Triggers order confirmation, inventory commitment, and customer notification.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PaymentCompletedEvent extends BaseEvent {

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
     * Payment amount
     */
    private BigDecimal amount;

    /**
     * Currency code (e.g., USD, EUR)
     */
    private String currency;

    /**
     * Payment method (CREDIT_CARD, DEBIT_CARD, PAYPAL, etc.)
     */
    private String paymentMethod;

    /**
     * Payment gateway transaction ID
     */
    private String transactionId;

    /**
     * Payment intent ID (from payment gateway)
     */
    private String paymentIntentId;

    /**
     * Last 4 digits of card (for display purposes)
     */
    private String cardLast4;

    /**
     * Card brand (Visa, Mastercard, etc.)
     */
    private String cardBrand;

    /**
     * Payment processing fee
     */
    private BigDecimal processingFee;

    @Override
    public String getPartitionKey() {
        return orderId;
    }
}
