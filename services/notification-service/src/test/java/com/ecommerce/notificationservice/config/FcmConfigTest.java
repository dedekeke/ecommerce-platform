package com.ecommerce.notificationservice.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the fail-fast validation on the FCM push wiring: selecting the fcm
 * provider without a credentials path must surface an actionable message
 * instead of an opaque IOException from the file open.
 */
class FcmConfigTest {

    private final FcmConfig fcmConfig = new FcmConfig();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void should_failFastWithActionableMessage_when_credentialsPathBlank(String credentialsPath) {
        FcmProperties properties = new FcmProperties();
        properties.setCredentialsPath(credentialsPath);
        properties.setProjectId("some-project");

        assertThatThrownBy(() -> fcmConfig.firebaseMessaging(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FCM_CREDENTIALS_PATH is required when PUSH_PROVIDER=fcm");
    }
}
