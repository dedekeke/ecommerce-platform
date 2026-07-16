package com.ecommerce.notificationservice.service.push;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FcmPushProvider}. The Firebase SDK is mocked at the {@link FcmMessageClient}
 * boundary so no real credentials or network calls are involved.
 */
class FcmPushProviderTest {

    private FcmMessageClient messageClient;
    private FcmPushProvider provider;

    @BeforeEach
    void setUp() {
        messageClient = mock(FcmMessageClient.class);
        provider = new FcmPushProvider(messageClient);
    }

    @Test
    void should_delegateToFcmClient_when_sending() {
        when(messageClient.send(anyString(), anyString(), anyString(), any())).thenReturn("msg-1");

        assertThatCode(() -> provider.send("token123", "Title", "Body", Map.of("k", "v")))
                .doesNotThrowAnyException();

        verify(messageClient).send(eq("token123"), eq("Title"), eq("Body"), eq(Map.of("k", "v")));
    }

    @Test
    void should_wrapInPushDeliveryException_when_fcmClientFails() {
        when(messageClient.send(anyString(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("FCM unavailable"));

        assertThatThrownBy(() -> provider.send("token123", "Title", "Body", Map.of()))
                .isInstanceOf(PushDeliveryException.class)
                .hasMessageContaining("FCM")
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
