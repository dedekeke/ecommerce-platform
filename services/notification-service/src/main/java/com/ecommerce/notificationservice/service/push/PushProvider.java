package com.ecommerce.notificationservice.service.push;

import java.util.Map;

/**
 * Abstraction over a push-notification gateway.
 *
 * <p>Selected at runtime via {@code notification.push.provider}: {@code noop} (default) wires
 * {@link NoopPushProvider}, which only logs; {@code fcm} wires {@link FcmPushProvider} (Firebase
 * Cloud Messaging). Symmetric with the SMS and payment provider abstractions so no Firebase
 * credentials are required for tests or local development.</p>
 *
 * <p>Implementations MUST throw {@link PushDeliveryException} on a delivery failure so the calling
 * {@code NotificationService} can mark the notification FAILED/RETRYING rather than losing it.</p>
 */
public interface PushProvider {

    /**
     * @param deviceToken registration token of the target device
     * @param title       notification title
     * @param body        already-rendered notification body
     * @param data        optional key/value data payload (may be empty)
     * @throws PushDeliveryException when the message could not be handed off to the gateway
     */
    void send(String deviceToken, String title, String body, Map<String, Object> data);
}
