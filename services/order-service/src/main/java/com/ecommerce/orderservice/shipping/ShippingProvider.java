package com.ecommerce.orderservice.shipping;

/**
 * Seam over the shipment-creation step of order fulfillment.
 *
 * <p>Selected at runtime via {@code shipping.provider} ({@code SHIPPING_PROVIDER}
 * env var): {@code noop} (default) wires {@link NoopShippingProvider}, which
 * simply echoes the carrier + tracking number an operator entered manually;
 * {@code external} wires {@link ExternalShippingProvider}, the placeholder for a
 * real carrier (FedEx / DHL / UPS) integration. This mirrors the payment-service
 * {@code PAYMENT_PROVIDER=mock|stripe} and notification-service
 * {@code notification.sms.provider=noop|twilio} patterns, so no carrier
 * credentials are ever required for tests or local development.</p>
 *
 * <p>Bounded scope: one shipment per order. The returned {@link Shipment} is the
 * authoritative carrier + tracking number to persist on the order — for the noop
 * provider that is exactly what the operator supplied; a real provider would
 * return the carrier-assigned tracking number from the booking call.</p>
 */
public interface ShippingProvider {

    /**
     * Create (or record) the outbound shipment for an order.
     *
     * @param request the order identity plus the operator-supplied carrier /
     *                tracking hint
     * @return the authoritative carrier + tracking number to persist
     */
    Shipment createShipment(ShipmentRequest request);

    /** Inputs to {@link #createShipment(ShipmentRequest)}. */
    record ShipmentRequest(String orderId, String orderNumber, String carrier, String trackingNumber) {}

    /** Authoritative shipment identity to persist on the order. */
    record Shipment(String carrier, String trackingNumber) {}
}
