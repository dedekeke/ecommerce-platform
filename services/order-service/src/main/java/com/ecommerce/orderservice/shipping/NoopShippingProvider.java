package com.ecommerce.orderservice.shipping;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default (manual) shipping provider: the operator enters the carrier + tracking
 * number themselves and this provider records them verbatim. Active when
 * {@code shipping.provider} is {@code noop} or unset, so no carrier credentials
 * are ever required for tests or local development.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "shipping.provider", havingValue = "noop", matchIfMissing = true)
public class NoopShippingProvider implements ShippingProvider {

    @Override
    public Shipment createShipment(ShipmentRequest request) {
        log.info("[noop-shipping] Recording operator-entered shipment for order {} — carrier={}, tracking={}",
            request.orderNumber(), request.carrier(), request.trackingNumber());
        return new Shipment(request.carrier(), request.trackingNumber());
    }
}
