package com.ecommerce.paymentservice.customer;

import com.ecommerce.paymentservice.config.StripeRequestOptionsFactory;
import com.ecommerce.paymentservice.savedmethod.PaymentProviderException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.param.CustomerCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Resolves the Stripe Customer id for an internal user, creating it on first use (PR#146
 * defense-in-depth #2).
 *
 * <p>The resolved customer is attached to the add-card SetupIntent and the checkout PaymentIntent
 * so Stripe itself binds a saved payment method to its owning customer — a second layer behind the
 * server-side ownership check. Guests (blank userId) get no customer.</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "payment.provider", havingValue = "stripe")
public class StripeCustomerService {

    private final StripeCustomerRepository repository;
    private final StripeRequestOptionsFactory requestOptions;

    public StripeCustomerService(StripeCustomerRepository repository,
                                 StripeRequestOptionsFactory requestOptions) {
        this.repository = repository;
        this.requestOptions = requestOptions;
    }

    /**
     * Returns the Stripe Customer id for {@code userId}, creating and persisting the mapping on
     * first use. A blank userId (guest checkout) yields {@code null} — no customer is created.
     * Concurrent first-time creates converge on the single row via the unique userId constraint.
     */
    public String getOrCreateCustomerId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        return repository.findByUserId(userId)
                .map(StripeCustomer::getCustomerId)
                .orElseGet(() -> createAndPersist(userId));
    }

    private String createAndPersist(String userId) {
        String customerId = createStripeCustomer(userId);
        try {
            repository.save(StripeCustomer.builder()
                    .userId(userId)
                    .customerId(customerId)
                    .build());
            log.debug("Stripe-created customer for user={} -> {}", userId, customerId);
            return customerId;
        } catch (DataIntegrityViolationException e) {
            // A concurrent request won the race and already persisted the mapping. Re-read and use
            // that row; the extra Stripe customer we created is harmless and left unreferenced.
            log.debug("Concurrent Stripe customer create for user={}, re-reading existing mapping", userId);
            return repository.findByUserId(userId)
                    .map(StripeCustomer::getCustomerId)
                    .orElseThrow(() -> new PaymentProviderException(
                            "Failed to resolve Stripe customer for user " + userId, e));
        }
    }

    private String createStripeCustomer(String userId) {
        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .putMetadata("userId", userId)
                    .build();
            Customer customer = Customer.create(params, requestOptions.build());
            return customer.getId();
        } catch (StripeException e) {
            log.error("Stripe createCustomer failed for user={}: {}", userId, e.getMessage());
            throw new PaymentProviderException("Failed to create Stripe customer", e);
        }
    }
}
