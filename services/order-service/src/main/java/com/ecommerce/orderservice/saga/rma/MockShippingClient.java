package com.ecommerce.orderservice.saga.rma;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stand-in for a real carrier integration (FedEx / DHL / UPS).
 *
 * <p>Generates a deterministic-looking but fake {@code returnLabelUrl}
 * on demand. A future PR can swap this implementation for one talking
 * to the real carrier SDK without touching the orchestrator or
 * controller.</p>
 */
@Slf4j
@Component
public class MockShippingClient {

    private static final String LABEL_URL_PREFIX = "https://shipping.mock/labels/";

    /**
     * Mock outbound-return label generation. Always succeeds.
     *
     * @return URL pointing at a fake PDF — the customer clicks this in the
     *         email to print the return slip.
     */
    public String generateReturnLabel(String rmaNumber, String orderId) {
        String url = LABEL_URL_PREFIX + UUID.randomUUID();
        log.info("MockShippingClient: issued return label {} for RMA {} (order {})",
            url, rmaNumber, orderId);
        return url;
    }

    /**
     * Mock outbound carrier call to ship rejected merchandise back to the
     * customer. No-op aside from log; returns a fake tracking number.
     */
    public String shipBackToCustomer(String rmaNumber, String orderId) {
        String tracking = "MOCK-RTN-" + UUID.randomUUID();
        log.info("MockShippingClient: shipping rejected RMA {} back to customer of order {} — tracking {}",
            rmaNumber, orderId, tracking);
        return tracking;
    }
}
