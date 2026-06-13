package com.ecommerce.paymentservice.api;

import com.ecommerce.paymentservice.domain.Payment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/** Request/response DTOs for the checkout PaymentIntent endpoint. */
public final class PaymentIntentDtos {

    private PaymentIntentDtos() {
    }

    /**
     * Request to create a payment intent. The client receives a {@code clientSecret} back and
     * confirms the payment in the browser with Stripe.js.
     */
    public record CreateRequest(

            @NotBlank String orderId,

            @NotBlank String userId,

            @NotNull @DecimalMin(value = "0.50", message = "amount must be at least 0.50") BigDecimal amount,

            @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "currency must be a 3-letter ISO code") String currency
    ) {
    }

    /**
     * Response returned to the checkout client. {@code clientSecret} is what Stripe.js needs to
     * confirm the payment in the browser; the raw secret key never leaves the backend.
     */
    public record Response(
            Long paymentId,
            String paymentIntentId,
            String clientSecret,
            String status
    ) {
        public static Response from(Payment payment) {
            return new Response(
                    payment.getId(),
                    payment.getPaymentIntentId(),
                    payment.getClientSecret(),
                    payment.getStatus().name());
        }
    }
}
