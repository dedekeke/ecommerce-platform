package com.ecommerce.notificationservice.service.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Real push provider backed by Firebase Cloud Messaging (activated when
 * {@code notification.push.provider=fcm}).
 *
 * <p>Delegates the actual SDK call to {@link FcmMessageClient} and translates any SDK failure into a
 * {@link PushDeliveryException} so the notification is marked FAILED/RETRYING rather than silently
 * lost. Firebase credentials live in {@code FcmProperties} (env-only) and never reach this class.</p>
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "notification.push.provider", havingValue = "fcm")
public class FcmPushProvider implements PushProvider {

    private final FcmMessageClient messageClient;

    public FcmPushProvider(FcmMessageClient messageClient) {
        this.messageClient = messageClient;
    }

    @Override
    public void send(String deviceToken, String title, String body, Map<String, Object> data) {
        try {
            String messageId = messageClient.send(deviceToken, title, body, data);
            log.info("[fcm] Push sent to {} (messageId={})", deviceToken, messageId);
        } catch (RuntimeException e) {
            log.error("[fcm] Failed to send push to {}: {}", deviceToken, e.getMessage());
            throw new PushDeliveryException("Failed to send push via FCM", e);
        }
    }
}
