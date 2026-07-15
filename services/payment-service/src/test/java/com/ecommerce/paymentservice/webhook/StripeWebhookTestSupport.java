package com.ecommerce.paymentservice.webhook;

import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.net.ApiResource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Builds Stripe webhook payloads and valid {@code Stripe-Signature} headers for
 * tests, using the same HMAC-SHA256 scheme Stripe uses — so tests exercise the
 * real {@link com.stripe.net.Webhook#constructEvent} verification path rather
 * than a stubbed signature.
 */
final class StripeWebhookTestSupport {

    private StripeWebhookTestSupport() {
    }

    /** Minimal event JSON whose {@code api_version} matches the SDK so getObject() resolves. */
    static String succeededEventJson(String eventId, String intentId, String chargeId) {
        return """
                {"id":"%s","object":"event","api_version":"%s","type":"payment_intent.succeeded",
                 "data":{"object":{"id":"%s","object":"payment_intent","status":"succeeded",
                 "latest_charge":"%s","amount":4200,"currency":"usd"}}}"""
                .formatted(eventId, Stripe.API_VERSION, intentId, chargeId);
    }

    static String failedEventJson(String eventId, String intentId, String message) {
        return """
                {"id":"%s","object":"event","api_version":"%s","type":"payment_intent.payment_failed",
                 "data":{"object":{"id":"%s","object":"payment_intent","status":"requires_payment_method",
                 "last_payment_error":{"message":"%s"}}}}"""
                .formatted(eventId, Stripe.API_VERSION, intentId, message);
    }

    static String canceledEventJson(String eventId, String intentId) {
        return """
                {"id":"%s","object":"event","api_version":"%s","type":"payment_intent.canceled",
                 "data":{"object":{"id":"%s","object":"payment_intent","status":"canceled"}}}"""
                .formatted(eventId, Stripe.API_VERSION, intentId);
    }

    static String unhandledEventJson(String eventId) {
        return """
                {"id":"%s","object":"event","api_version":"%s","type":"charge.refunded",
                 "data":{"object":{"id":"ch_x","object":"charge"}}}"""
                .formatted(eventId, Stripe.API_VERSION);
    }

    static Event parse(String json) {
        return ApiResource.GSON.fromJson(json, Event.class);
    }

    /** Builds a valid {@code t=...,v1=...} header for the payload under the secret. */
    static String signature(String payload, String secret) {
        return signature(payload, secret, Instant.now().getEpochSecond());
    }

    static String signature(String payload, String secret, long timestamp) {
        String signedPayload = timestamp + "." + payload;
        return "t=" + timestamp + ",v1=" + hmacSha256(signedPayload, secret);
    }

    private static String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute test HMAC", e);
        }
    }
}
