package com.ecommerce.paymentservice.gateway;

import com.ecommerce.paymentservice.config.StripeRequestOptionsFactory;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentConfirmParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Real Stripe PaymentIntent gateway backed by the official Stripe Java SDK
 * (activated when {@code payment.provider=stripe}).
 *
 * <p>Creates a PaymentIntent and returns its {@code client_secret} so the browser can confirm the
 * payment with Stripe.js. Secret key and base URL are supplied by {@link StripeRequestOptionsFactory}
 * — the live key is never hardcoded. Resilience4j wraps every call to protect against Stripe
 * outages and latency spikes.</p>
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "payment.provider", havingValue = "stripe")
public class StripePaymentIntentProvider implements PaymentIntentProvider {

    private final StripeRequestOptionsFactory requestOptions;

    public StripePaymentIntentProvider(StripeRequestOptionsFactory requestOptions) {
        this.requestOptions = requestOptions;
    }

    @Override
    @CircuitBreaker(name = "payment-gateway", fallbackMethod = "createPaymentIntentFallback")
    @Retry(name = "payment-gateway")
    @Bulkhead(name = "payment-gateway")
    public PaymentGatewayResponse createPaymentIntent(String orderId, String userId, BigDecimal amount, String currency) {
        log.info("[stripe] Creating payment intent for order: {}, amount: {} {}", orderId, amount, currency);
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(toMinorUnits(amount))
                    .setCurrency(currency.toLowerCase())
                    .putMetadata("orderId", orderId)
                    .putMetadata("userId", userId)
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build())
                    .build();

            // Deterministic idempotency key keyed on the order: a Resilience4j retry of this same
            // create call is deduplicated by Stripe and returns the original intent, never a second charge.
            PaymentIntent intent = PaymentIntent.create(params, requestOptions.build(orderId));

            return PaymentGatewayResponse.builder()
                    .success(true)
                    .paymentIntentId(intent.getId())
                    .clientSecret(intent.getClientSecret())
                    .status(mapStatus(intent.getStatus()))
                    .build();
        } catch (StripeException e) {
            log.error("[stripe] createPaymentIntent failed for order {}: {}", orderId, e.getMessage());
            return failure(null, e);
        }
    }

    @Override
    @CircuitBreaker(name = "payment-gateway", fallbackMethod = "confirmPaymentFallback")
    @Retry(name = "payment-gateway")
    @Bulkhead(name = "payment-gateway")
    public PaymentGatewayResponse confirmPayment(String paymentIntentId, String paymentMethodId) {
        log.info("[stripe] Confirming payment for intent: {}", paymentIntentId);
        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId, requestOptions.build());

            PaymentIntentConfirmParams.Builder confirm = PaymentIntentConfirmParams.builder();
            if (StringUtils.hasText(paymentMethodId)) {
                confirm.setPaymentMethod(paymentMethodId);
            }
            PaymentIntent confirmed = intent.confirm(confirm.build(), requestOptions.build());

            boolean success = "succeeded".equalsIgnoreCase(confirmed.getStatus());
            return PaymentGatewayResponse.builder()
                    .success(success)
                    .paymentIntentId(confirmed.getId())
                    .transactionId(success ? confirmed.getLatestCharge() : null)
                    .status(mapStatus(confirmed.getStatus()))
                    .errorMessage(success ? null : "Payment not completed. Status: " + confirmed.getStatus())
                    .build();
        } catch (StripeException e) {
            log.error("[stripe] confirmPayment failed for intent {}: {}", paymentIntentId, e.getMessage());
            return failure(paymentIntentId, e);
        }
    }

    @Override
    @CircuitBreaker(name = "payment-gateway", fallbackMethod = "refundPaymentFallback")
    @Retry(name = "payment-gateway")
    @Bulkhead(name = "payment-gateway")
    public PaymentGatewayResponse refundPayment(String paymentIntentId, BigDecimal amount, String reason) {
        log.info("[stripe] Refunding payment intent: {}, amount: {}", paymentIntentId, amount);
        try {
            RefundCreateParams.Builder params = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId);
            if (amount != null) {
                params.setAmount(toMinorUnits(amount));
            }
            RefundCreateParams.Reason mappedReason = mapRefundReason(reason);
            if (mappedReason != null) {
                params.setReason(mappedReason);
            }
            // Idempotency key derived from the intent so a retried refund is not applied twice.
            Refund refund = Refund.create(params.build(), requestOptions.build(paymentIntentId + ":refund"));

            return PaymentGatewayResponse.builder()
                    .success(true)
                    .paymentIntentId(paymentIntentId)
                    .transactionId(refund.getId())
                    .status("REFUNDED")
                    .build();
        } catch (StripeException e) {
            log.error("[stripe] refundPayment failed for intent {}: {}", paymentIntentId, e.getMessage());
            return failure(paymentIntentId, e);
        }
    }

    /**
     * Maps a free-text reason onto Stripe's restricted refund reason enum. Stripe only accepts
     * {@code duplicate}, {@code fraudulent} and {@code requested_by_customer}; anything else (or
     * blank) is omitted so the SDK does not reject the request.
     */
    private RefundCreateParams.Reason mapRefundReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        return switch (reason.trim().toLowerCase()) {
            case "duplicate" -> RefundCreateParams.Reason.DUPLICATE;
            case "fraudulent" -> RefundCreateParams.Reason.FRAUDULENT;
            case "requested_by_customer" -> RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER;
            default -> null;
        };
    }

    private long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Maps Stripe's PaymentIntent status vocabulary onto the internal gateway status. */
    private String mapStatus(String stripeStatus) {
        if (stripeStatus == null) {
            return "PENDING";
        }
        return switch (stripeStatus) {
            case "succeeded" -> "COMPLETED";
            case "processing" -> "PROCESSING";
            case "canceled" -> "FAILED";
            default -> "PENDING";
        };
    }

    private PaymentGatewayResponse failure(String paymentIntentId, StripeException e) {
        return PaymentGatewayResponse.builder()
                .success(false)
                .paymentIntentId(paymentIntentId)
                .status("FAILED")
                .errorMessage(e.getMessage())
                .build();
    }

    PaymentGatewayResponse createPaymentIntentFallback(String orderId, String userId,
            BigDecimal amount, String currency, Throwable t) {
        log.error("[stripe] gateway unavailable creating intent. Order: {}, Error: {}", orderId, t.getMessage());
        return PaymentGatewayResponse.builder()
                .success(false)
                .status("GATEWAY_UNAVAILABLE")
                .errorMessage("Payment gateway is temporarily unavailable. Please try again later.")
                .build();
    }

    PaymentGatewayResponse confirmPaymentFallback(String paymentIntentId,
            String paymentMethodId, Throwable t) {
        log.error("[stripe] gateway unavailable confirming intent {}. Error: {}", paymentIntentId, t.getMessage());
        return PaymentGatewayResponse.builder()
                .success(false)
                .paymentIntentId(paymentIntentId)
                .status("GATEWAY_UNAVAILABLE")
                .errorMessage("Payment gateway is temporarily unavailable. Your payment has not been processed.")
                .build();
    }

    PaymentGatewayResponse refundPaymentFallback(String paymentIntentId,
            BigDecimal amount, String reason, Throwable t) {
        log.error("[stripe] gateway unavailable refunding intent {}. Error: {}", paymentIntentId, t.getMessage());
        return PaymentGatewayResponse.builder()
                .success(false)
                .paymentIntentId(paymentIntentId)
                .status("REFUND_PENDING")
                .errorMessage("Refund request queued. Will be processed when gateway is available.")
                .build();
    }
}
