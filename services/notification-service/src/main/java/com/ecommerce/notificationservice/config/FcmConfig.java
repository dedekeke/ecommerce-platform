package com.ecommerce.notificationservice.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Wires the Firebase Admin SDK, but only when {@code notification.push.provider=fcm}. The
 * service-account credentials are loaded from the path in {@link FcmProperties} (env-only) — no
 * credentials are embedded in the image, mirroring how the Stripe secret key is handled in
 * payment-service.
 */
@Configuration
@Slf4j
@ConditionalOnProperty(name = "notification.push.provider", havingValue = "fcm")
public class FcmConfig {

    @Bean
    public FirebaseMessaging firebaseMessaging(FcmProperties properties) throws IOException {
        // Fail fast with an actionable message rather than an opaque IOException
        // from Files.newInputStream when the operator selects FCM but forgets the
        // credentials path.
        if (!StringUtils.hasText(properties.getCredentialsPath())) {
            throw new IllegalStateException(
                    "FCM_CREDENTIALS_PATH is required when PUSH_PROVIDER=fcm");
        }
        try (InputStream credentials = Files.newInputStream(Path.of(properties.getCredentialsPath()))) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .setProjectId(properties.getProjectId())
                    .build();
            // Reuse the named app on context restarts (initializeApp throws if the name already exists).
            FirebaseApp app = FirebaseApp.getApps().stream()
                    .filter(a -> "notification-service".equals(a.getName()))
                    .findFirst()
                    .orElseGet(() -> FirebaseApp.initializeApp(options, "notification-service"));
            log.info("Firebase Cloud Messaging initialised for project {}", properties.getProjectId());
            return FirebaseMessaging.getInstance(app);
        }
    }
}
