package com.ecommerce.notificationservice.service.push;

import java.util.Map;

/**
 * Thin seam over the Firebase Admin SDK ({@code FirebaseMessaging.send(Message)}). Isolating the SDK
 * behind an interface keeps {@link FcmPushProvider}'s error-handling logic unit-testable without
 * mocking Firebase's final classes or hitting the network.
 */
public interface FcmMessageClient {

    /**
     * @return the provider-side message id
     * @throws RuntimeException propagated from the Firebase SDK on any failure
     */
    String send(String deviceToken, String title, String body, Map<String, Object> data);
}
