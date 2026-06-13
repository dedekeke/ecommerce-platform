package com.ecommerce.paymentservice.savedmethod;

import com.ecommerce.paymentservice.config.StripeRequestOptionsFactory;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Real Stripe-backed tokenization adapter for §3.9 (activated when {@code payment.provider=stripe}).
 *
 * <p>The {@code token} is a Stripe PaymentMethod id ({@code pm_...}) returned by Stripe.js on the
 * client. The adapter retrieves the PaymentMethod from Stripe to obtain display-only card
 * metadata (last4/brand/exp); no PAN ever crosses this boundary. The secret key and base URL come
 * from {@link StripeRequestOptionsFactory}, so the live key is never hardcoded.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "stripe")
public class StripePaymentProviderAdapter implements PaymentProviderAdapter {

    static final String PROVIDER_NAME = "STRIPE";

    private final StripeRequestOptionsFactory requestOptions;

    public StripePaymentProviderAdapter(StripeRequestOptionsFactory requestOptions) {
        this.requestOptions = requestOptions;
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public AttachResult attachPaymentMethod(String userId, String token) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("token must not be blank");
        }
        try {
            PaymentMethod pm = PaymentMethod.retrieve(token, requestOptions.build());
            PaymentMethod.Card card = pm.getCard();
            if (card == null) {
                throw new IllegalArgumentException("Stripe payment method " + token + " is not a card");
            }
            log.debug("Stripe-attached payment method for user={} -> providerId={}", userId, pm.getId());
            return new AttachResult(
                    pm.getId(),
                    card.getLast4(),
                    normalizeBrand(card.getBrand()),
                    toInt(card.getExpMonth()),
                    toInt(card.getExpYear()));
        } catch (StripeException e) {
            log.error("Stripe attachPaymentMethod failed for user={} token={}: {}", userId, token, e.getMessage());
            throw new PaymentProviderException("Failed to attach payment method with Stripe", e);
        }
    }

    private static String normalizeBrand(String brand) {
        return brand == null ? null : brand.toUpperCase();
    }

    private static Integer toInt(Long value) {
        return value == null ? null : value.intValue();
    }
}
