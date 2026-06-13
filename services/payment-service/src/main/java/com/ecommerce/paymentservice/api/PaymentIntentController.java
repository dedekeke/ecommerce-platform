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
            @Valid @RequestBody PaymentIntentDtos.CreateRequest request) {
        log.info("REST: create payment intent for order {}", request.orderId());

        Payment payment = paymentService.createPaymentIntent(
                request.orderId(), request.userId(), request.amount(), request.currency());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(PaymentIntentDtos.Response.from(payment));
    }
}
