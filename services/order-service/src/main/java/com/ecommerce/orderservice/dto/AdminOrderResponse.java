package com.ecommerce.orderservice.dto;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin-facing view of an {@link Order} returned by the admin list endpoint
 * ({@code GET /api/orders}).
 *
 * <p>Deliberately distinct from the customer-facing {@link OrderResponse}. That
 * DTO strips ownership/PII so a shopper can never read another customer's
 * identity; admins, by contrast, MUST see who placed an order to manage it —
 * so this DTO restores {@code userId}, {@code guestEmail}, and the
 * {@code guestOrder} flag.
 *
 * <p>Secrets remain excluded: the Stripe {@code paymentClientSecret} and the
 * {@code paymentIntentId} are never mapped here (the client secret is served
 * exclusively via {@link CheckoutResponse}). An admin managing fulfilment does
 * not need the payment intent, so it is intentionally left out to keep the
 * exposed surface minimal.
 */
public record AdminOrderResponse(
    String orderId,
    String orderNumber,
    String userId,
    String guestEmail,
    boolean guestOrder,
    String status,
    String currency,
    BigDecimal total,
    int itemCount,
    List<Item> items,
    String carrier,
    String trackingNumber,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    /**
     * Currency is not persisted on the order; mirror the read contract used by
     * {@link OrderResponse} so the admin table renders a consistent currency.
     */
    static final String DEFAULT_CURRENCY = "USD";

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

    /**
     * Maps an {@link Order} entity to its admin response view — restoring the
     * customer identity ({@code userId}, {@code guestEmail}, {@code guestOrder})
     * while never mapping the payment secret / intent.
     */
    public static AdminOrderResponse from(Order order) {
        List<Item> items = order.getItems() == null
            ? List.of()
            : order.getItems().stream().map(Item::from).toList();

        return new AdminOrderResponse(
            order.getId(),
            order.getOrderNumber(),
            order.getUserId(),
            order.getGuestEmail(),
            order.isGuestOrder(),
            order.getStatus() != null ? order.getStatus().name() : null,
            DEFAULT_CURRENCY,
            order.getTotal(),
            items.size(),
            items,
            order.getCarrier(),
            order.getTrackingNumber(),
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }
}
