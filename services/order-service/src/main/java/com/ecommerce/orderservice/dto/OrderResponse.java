package com.ecommerce.orderservice.dto;

import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Client-safe view of an {@link Order} returned by the read/mutation endpoints
 * ({@code GET /{orderId}}, {@code GET /number/{orderNumber}},
 * {@code GET /user/{userId}}, {@code PUT /{orderId}/status},
 * {@code POST /{orderId}/cancel}).
 *
 * <p>Introduced to stop serializing the raw JPA entity, which leaked internal
 * state — most critically the Stripe {@code paymentClientSecret} (previously
 * masked with an entity-level {@code @JsonIgnore} workaround) — and exposed
 * ownership/PII fields ({@code userId}, {@code guestEmail}) and audit internals.
 * Only the fields below are exposed; the payment client secret is served
 * exclusively through {@link CheckoutResponse} on the checkout create/replay
 * path that legitimately needs it.</p>
 *
 * <p>Field naming mirrors {@link CheckoutResponse} so a single client model
 * covers both the checkout response and later order reads.</p>
 */
public record OrderResponse(
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
    List<Item> items,
    ShippingAddress shippingAddress,
    String paymentIntentId,
    boolean guestOrder,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    /**
     * Currency is not persisted on the order; it mirrors the saga's
     * {@code PAYMENT_CURRENCY} ("USD") so the read contract matches
     * {@link CheckoutResponse#currency()}.
     */
    private static final String DEFAULT_CURRENCY = "USD";

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

    /** Shipping address fields only — no PII beyond the delivery address itself. */
    public record ShippingAddress(
        String street,
        String city,
        String state,
        String postalCode,
        String country
    ) {
        static ShippingAddress from(Address address) {
            if (address == null) {
                return null;
            }
            return new ShippingAddress(
                address.getStreet(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry()
            );
        }
    }

    /** Maps an {@link Order} entity to its client-safe response view. */
    public static OrderResponse from(Order order) {
        return new OrderResponse(
            order.getId(),
            order.getOrderNumber(),
            order.getStatus() != null ? order.getStatus().name() : null,
            DEFAULT_CURRENCY,
            order.getSubtotal(),
            order.getTax(),
            order.getShippingCost(),
            order.getDiscountAmount(),
            order.getLoyaltyDiscount(),
            order.getTotal(),
            order.getItems() == null ? List.of()
                : order.getItems().stream().map(Item::from).toList(),
            ShippingAddress.from(order.getShippingAddress()),
            order.getPaymentIntentId(),
            order.isGuestOrder(),
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }
}
