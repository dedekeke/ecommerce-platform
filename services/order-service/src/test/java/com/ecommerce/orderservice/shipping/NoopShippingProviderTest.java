package com.ecommerce.orderservice.shipping;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the default (manual) shipping provider: it must echo the
 * operator-supplied carrier + tracking verbatim.
 */
class NoopShippingProviderTest {

    private final NoopShippingProvider provider = new NoopShippingProvider();

    @Test
    void should_echoOperatorSuppliedCarrierAndTracking_when_createShipment() {
        ShippingProvider.Shipment shipment = provider.createShipment(
            new ShippingProvider.ShipmentRequest("order-1", "ORD-1", "UPS", "1Z999"));

        assertThat(shipment.carrier()).isEqualTo("UPS");
        assertThat(shipment.trackingNumber()).isEqualTo("1Z999");
    }
}
