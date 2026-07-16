package com.ecommerce.notificationservice.service.sms;

/**
 * Thin seam over the static Twilio SDK call ({@code Message.creator(...).create()}). Isolating the
 * SDK behind an interface keeps {@link TwilioSmsProvider}'s error-handling logic unit-testable
 * without static mocking or real network calls.
 */
public interface TwilioMessageClient {

    /**
     * @return the provider-side message SID
     * @throws RuntimeException propagated from the Twilio SDK on any failure
     */
    String sendMessage(String toPhoneNumber, String body);
}
