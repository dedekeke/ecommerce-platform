package com.ecommerce.reviewservice.client;

/**
 * Minimal abstraction over order-service used purely to verify whether a user
 * has actually purchased a product. Implementations are expected to fail-open
 * (return {@code false}) on errors rather than throwing — see
 * {@code RestOrderServiceClient}.
 */
public interface OrderServiceClient {

    /**
     * Returns true if {@code userId} has at least one completed order containing
     * {@code productId}. Returns false on errors, timeouts, or when the user
     * has no matching order.
     */
    boolean hasUserPurchasedProduct(String userId, String productId);
}
