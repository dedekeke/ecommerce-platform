package com.ecommerce.orderservice.saga.rma;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MockShippingClient")
class MockShippingClientTest {

    private final MockShippingClient client = new MockShippingClient();

    @Test
    @DisplayName("generateReturnLabel_should_returnFakeUrl_withMockPrefix")
    void generateReturnLabel_should_returnFakeUrl_withMockPrefix() {
        String url = client.generateReturnLabel("RMA-1", "order-1");
        assertThat(url).startsWith("https://shipping.mock/labels/").hasSizeGreaterThan(40);
    }

    @Test
    @DisplayName("generateReturnLabel_should_returnUniqueUrls_perCall")
    void generateReturnLabel_should_returnUniqueUrls_perCall() {
        String a = client.generateReturnLabel("RMA-1", "order-1");
        String b = client.generateReturnLabel("RMA-1", "order-1");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("shipBackToCustomer_should_returnTrackingNumber_withRtnPrefix")
    void shipBackToCustomer_should_returnTrackingNumber_withRtnPrefix() {
        String tracking = client.shipBackToCustomer("RMA-1", "order-1");
        assertThat(tracking).startsWith("MOCK-RTN-");
    }
}
