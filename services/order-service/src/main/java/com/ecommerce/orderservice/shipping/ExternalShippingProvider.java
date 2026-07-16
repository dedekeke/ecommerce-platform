package com.ecommerce.orderservice.shipping;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Placeholder seam for a real carrier integration (FedEx / DHL / UPS), selected
 * with {@code shipping.provider=external} ({@code SHIPPING_PROVIDER=external}).
 *
 * <p>Intentionally NOT implemented: this PR delivers only the fulfillment
 * foundation and the provider seam, not a carrier integration. The bean exists
 * so the selection wiring is real and testable; a future PR replaces the body
 * with an actual carrier-SDK booking call, without touching the controller,
 * service, or event path.</p>
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "shipping.provider", havingValue = "external")
public class ExternalShippingProvider implements ShippingProvider {

    @Override
    public Shipment createShipment(ShipmentRequest request) {
        throw new UnsupportedOperationException(
            "External carrier shipping provider is not implemented. Set SHIPPING_PROVIDER=noop "
                + "(default) for operator-entered tracking, or implement a real carrier integration "
                + "behind this seam.");
    }
}
