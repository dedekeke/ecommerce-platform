package com.ecommerce.paymentservice.api;

import com.ecommerce.paymentservice.domain.Payment;
import com.ecommerce.paymentservice.savedmethod.SavedPaymentMethodService;
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
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final SavedPaymentMethodService savedPaymentMethodService;

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

    @PostMapping("/intents/confirm-saved")
    @Operation(summary = "Confirm an order's payment with the caller's saved method (server-verified ownership)")
    public ResponseEntity<PaymentIntentDtos.Response> confirmWithSavedMethod(
            @Valid @RequestBody PaymentIntentDtos.SavedMethodPayRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {

        // Ownership is enforced server-side: the browser sends a saved payment_method id, but the
        // service confirms the PaymentIntent only after proving that id belongs to this JWT subject.
        String userId = requireUserId(jwt, headerUserId);
        log.info("REST: confirm payment intent {} with a saved method (user {})",
                request.paymentIntentId(), userId);

        Payment payment = savedPaymentMethodService.payWithSavedMethod(
                userId, request.paymentIntentId(), request.paymentMethodId());

        return ResponseEntity.ok(PaymentIntentDtos.Response.from(payment));
    }

    private String requireUserId(Jwt jwt, String headerUserId) {
        if (jwt != null && StringUtils.hasText(jwt.getSubject())) {
            return jwt.getSubject();
        }
        if (StringUtils.hasText(headerUserId)) {
            return headerUserId;
        }
        throw new SecurityException("Authenticated user required");
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
