package com.ecommerce.notificationservice.service.push;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Real {@link FcmMessageClient} backed by the Firebase Admin SDK. Instantiated only when
 * {@code notification.push.provider=fcm} (see {@code FcmConfig}). Serialises the data payload to
 * strings as required by FCM and surfaces SDK failures as unchecked so {@link FcmPushProvider}
 * wraps them into a {@link PushDeliveryException}.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "notification.push.provider", havingValue = "fcm")
public class FirebaseFcmMessageClient implements FcmMessageClient {

    private final FirebaseMessaging firebaseMessaging;

    public FirebaseFcmMessageClient(FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    @Override
    public String send(String deviceToken, String title, String body, Map<String, Object> data) {
        Message.Builder builder = Message.builder()
                .setToken(deviceToken)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build());
        if (data != null) {
            data.forEach((key, value) -> builder.putData(key, String.valueOf(value)));
        }
        try {
            return firebaseMessaging.send(builder.build());
        } catch (FirebaseMessagingException e) {
            // Re-throw unchecked; FcmPushProvider owns the FAILED/RETRYING translation.
            throw new IllegalStateException("FCM send failed: " + e.getMessage(), e);
        }
    }
}
