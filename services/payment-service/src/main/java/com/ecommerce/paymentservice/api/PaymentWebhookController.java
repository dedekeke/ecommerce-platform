package com.ecommerce.paymentservice.api;

import com.ecommerce.paymentservice.webhook.PaymentWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Stripe webhook receiver — the AUTHORITATIVE settlement signal for a payment.
 *
 * <p>The client-side {@code confirmPayment} is a best-effort hint that can be
 * lost (closed tab, dropped connection) or deferred (3D Secure, delayed bank
 * debits). Stripe's server-to-server webhook is the source of truth, so this
 * endpoint reconciles the payment + order state on {@code payment_intent.*}
 * events.
 *
 * <p>Exempt from JWT auth (see SecurityConfig) because Stripe cannot present a
 * JWT; authentication is the {@code Stripe-Signature} HMAC verified against the
 * webhook signing secret. The body is read as RAW bytes — never a parsed and
 * re-serialized DTO — because the signature is computed over the exact payload.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payments", description = "Checkout payment intent operations")
public class PaymentWebhookController {

    private final PaymentWebhookService webhookService;

    @PostMapping("/webhook")
    @Operation(summary = "Receive Stripe webhook events (signature-verified); authoritative payment settlement")
    public ResponseEntity<String> handleWebhook(
            @RequestBody(required = false) byte[] payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {

        String rawPayload = payload == null ? "" : new String(payload, StandardCharsets.UTF_8);
        webhookService.handle(rawPayload, signature);
        // 200 quickly so Stripe does not retry a successfully-handled event.
        return ResponseEntity.ok("ok");
    }
}
