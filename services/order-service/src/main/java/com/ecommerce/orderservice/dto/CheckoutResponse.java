package com.ecommerce.orderservice.dto;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.saga.OrderCreationSaga.CheckoutResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response of {@code POST /api/orders} — the checkout contract consumed by the
 * checkout-mfe.
 *
 * <p>Payment sequencing: the order is created (status {@code PENDING}) and a
 * Stripe PaymentIntent is created server-side by the saga. {@code clientSecret}
 * is returned so the browser can confirm the payment with Stripe Elements. On
 * an idempotent replay of an already-completed order the {@code clientSecret} is
 * {@code null} (it is not persisted) — use {@code paymentIntentId} to recover
 * the secret from payment-service if payment still needs completing.</p>
 */
public record CheckoutResponse(
    String orderId,
    String orderNumber,
    String status,
    String currency,
    BigDecimal subtotal,
    BigDecimal tax,
    BigDecimal shippingCost,
    BigDecimal discountAmount,
    BigDecimal loyaltyDiscount,
    BigDecimal total,
    String paymentIntentId,
    String clientSecret,
    List<Item> items,
    LocalDateTime createdAt
) {

    public record Item(
        String productId,
        String productName,
        BigDecimal price,
        Integer quantity,
        BigDecimal subtotal
    ) {
        static Item from(OrderItem item) {
            return new Item(
                item.getProductId(),
                item.getProductName(),
                item.getPrice(),
                item.getQuantity(),
                item.getSubtotal()
            );
        }
    }

    /** Fresh checkout: carries the client secret needed to confirm payment. */
    public static CheckoutResponse from(CheckoutResult result) {
        return build(result.order(), result.currency(), result.paymentIntentId(), result.paymentClientSecret());
    }

    /**
     * Idempotent replay: the order already exists. The persisted client secret
     * is re-served so the owning session can still complete payment even if it
     * never saw the original response.
     */
    public static CheckoutResponse fromExistingOrder(Order order, String currency) {
        return build(order, currency, order.getPaymentIntentId(), order.getPaymentClientSecret());
    }

    private static CheckoutResponse build(Order order, String currency, String paymentIntentId, String clientSecret) {
        return new CheckoutResponse(
            order.getId(),
            order.getOrderNumber(),
            order.getStatus() != null ? order.getStatus().name() : null,
            currency,
            order.getSubtotal(),
            order.getTax(),
            order.getShippingCost(),
            order.getDiscountAmount(),
            order.getLoyaltyDiscount(),
            order.getTotal(),
            paymentIntentId,
            clientSecret,
            order.getItems() == null ? List.of()
                : order.getItems().stream().map(Item::from).toList(),
            order.getCreatedAt()
        );
    }
}
