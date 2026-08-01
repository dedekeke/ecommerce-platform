package com.ecommerce.notificationservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Firebase Cloud Messaging configuration for the push gateway.
 *
 * <p>{@code credentialsPath} points at a service-account JSON file and {@code projectId} identifies
 * the Firebase project; both come from environment variables ({@code FCM_CREDENTIALS_PATH},
 * {@code FCM_PROJECT_ID}) and are never committed. They are only required when
 * {@code notification.push.provider=fcm}; under the default {@code noop} provider they stay blank.</p>
 */
@Component
@ConfigurationProperties(prefix = "notification.push.fcm")
@Getter
@Setter
public class FcmProperties {

    private String credentialsPath;

    private String projectId;
}
