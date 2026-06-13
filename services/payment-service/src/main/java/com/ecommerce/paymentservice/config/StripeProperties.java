package com.ecommerce.paymentservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Stripe configuration shared by the Stripe-backed adapters.
 *
 * <p>The secret key is sourced from {@code STRIPE_SECRET_KEY} via the {@code stripe.secret-key}
 * placeholder and is never hardcoded or committed. {@code apiBase} is overridable so integration
 * tests can point the SDK at a WireMock-stubbed server instead of the live Stripe API.</p>
 */
@Component
@ConfigurationProperties(prefix = "stripe")
@Getter
@Setter
public class StripeProperties {

    private String secretKey;

    private String apiBase;
}
