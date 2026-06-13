package com.ecommerce.paymentservice.config;

import com.stripe.net.RequestOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Builds per-request {@link RequestOptions} for the Stripe SDK from {@link StripeProperties}.
 *
 * <p>Using per-request options (rather than mutating the global {@code Stripe.apiKey}) keeps the
 * Stripe adapters thread-safe and lets integration tests redirect the SDK to a stubbed base URL.
 * SDK-level network retries are disabled because Resilience4j owns the retry policy at the
 * adapter boundary.</p>
 */
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "stripe")
public class StripeRequestOptionsFactory {

    private final StripeProperties properties;

    public StripeRequestOptionsFactory(StripeProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validateConfig() {
        if (!StringUtils.hasText(properties.getSecretKey())) {
            throw new IllegalStateException(
                    "payment.provider=stripe requires a non-empty stripe.secret-key (STRIPE_SECRET_KEY)");
        }
    }

    public RequestOptions build() {
        return build(null);
    }

    /**
     * Builds request options with a deterministic Stripe idempotency key. Stripe deduplicates any
     * write request carrying the same key for 24h, so Resilience4j {@code @Retry} re-invocations
     * cannot create duplicate charges or refunds. A blank/null key yields plain options.
     */
    public RequestOptions build(String idempotencyKey) {
        RequestOptions.RequestOptionsBuilder builder = RequestOptions.builder()
                .setApiKey(properties.getSecretKey())
                .setMaxNetworkRetries(0);
        if (StringUtils.hasText(properties.getApiBase())) {
            builder.setBaseUrl(properties.getApiBase());
        }
        if (StringUtils.hasText(idempotencyKey)) {
            builder.setIdempotencyKey(idempotencyKey);
        }
        return builder.build();
    }
}
