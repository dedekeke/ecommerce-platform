package com.ecommerce.notificationservice.service;

import com.ecommerce.notificationservice.service.push.PushProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Push Notification Service
 *
 * <p>Thin channel service that delegates to the configured {@link PushProvider}
 * ({@code notification.push.provider}: {@code noop} default, {@code fcm} real). A provider delivery
 * failure surfaces as a {@code PushDeliveryException}, which {@code NotificationService} catches to
 * mark the notification FAILED/RETRYING — symmetric with email and SMS.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PushService {

    private final PushProvider pushProvider;

    /**
     * Send a push notification via the configured provider.
     */
    public void sendPush(String deviceToken, String title, String body, Map<String, Object> data) {
        pushProvider.send(deviceToken, title, body, data);
    }
}
