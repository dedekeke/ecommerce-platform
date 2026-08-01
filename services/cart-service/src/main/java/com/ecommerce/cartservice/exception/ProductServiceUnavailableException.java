package com.ecommerce.cartservice.exception;

/**
 * Raised when product-service is unavailable (circuit breaker open, bulkhead
 * saturated, connection/read failure or 5xx) and the cart operation cannot be
 * completed with fresh product data.
 *
 * <p>This is deliberately distinct from {@link ProductNotAvailableException}:
 * the latter means "we reached product-service and the product is genuinely
 * unusable" (not found, inactive, out of stock), whereas this means "we could
 * not reach product-service reliably right now". The operation must FAIL FAST
 * (HTTP 503) rather than serve stale prices or fabricated product data — the
 * caller may safely retry shortly.
 */
public class ProductServiceUnavailableException extends RuntimeException {

    public ProductServiceUnavailableException(String message) {
        super(message);
    }

    public ProductServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
