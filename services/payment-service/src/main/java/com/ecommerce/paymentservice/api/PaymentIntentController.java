package com.ecommerce.paymentservice.api;

import com.ecommerce.paymentservice.domain.Payment;
import com.ecommerce.paymentservice.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the checkout flow. Complements the existing gRPC API by exposing the
 * PaymentIntent creation step over HTTP so the browser can obtain a {@code clientSecret} and
 * confirm the payment with Stripe.js (replacing the legacy mock paymentMethodId handshake).
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payments", description = "Checkout payment intent operations")
public class PaymentIntentController {

    private final PaymentService paymentService;

    @PostMapping("/intents")
    @Operation(summary = "Create a payment intent and return its client secret for Stripe.js confirmation")
    public ResponseEntity<PaymentIntentDtos.Response> createPaymentIntent(
            @Valid @RequestBody PaymentIntentDtos.CreateRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        // The owning user is the token subject — never trusted from the request body. When the body
        // does carry a userId it is only a correlation hint and must match, otherwise we reject it.
        String userId = resolveUserId(request, jwt);
        log.info("REST: create payment intent for order {} (user {})", request.orderId(), userId);

        Payment payment = paymentService.createPaymentIntent(
                request.orderId(), userId, request.amount(), request.currency());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(PaymentIntentDtos.Response.from(payment));
    }

    private String resolveUserId(PaymentIntentDtos.CreateRequest request, Jwt jwt) {
        if (jwt == null) {
            // No authenticated principal (e.g. security disabled for local dev): fall back to the body.
            return request.userId();
        }
        String subject = jwt.getSubject();
        if (StringUtils.hasText(request.userId()) && !request.userId().equals(subject)) {
            throw new UserMismatchException("Request userId does not match the authenticated user");
        }
        return subject;
    }
}
