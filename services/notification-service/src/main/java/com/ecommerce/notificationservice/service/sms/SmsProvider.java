package com.ecommerce.notificationservice.service.sms;

/**
 * Abstraction over an external SMS gateway.
 *
 * <p>Selected at runtime via {@code notification.sms.provider}: {@code noop} (default) wires
 * {@link NoopSmsProvider}, which only logs and never leaves the process; {@code twilio} wires
 * {@link TwilioSmsProvider}. The noop keeps tests and local development free of any real Twilio
 * credentials, mirroring the payment-service {@code PAYMENT_PROVIDER=mock|stripe} pattern.</p>
 *
 * <p>Implementations MUST throw {@link SmsDeliveryException} on a delivery failure so the calling
 * {@code NotificationService} can mark the notification FAILED/RETRYING rather than losing it.</p>
 */
public interface SmsProvider {

    /**
     * @param phoneNumber E.164 recipient number
     * @param message     already-rendered message body
     * @throws SmsDeliveryException when the message could not be handed off to the gateway
     */
    void send(String phoneNumber, String message);
}
