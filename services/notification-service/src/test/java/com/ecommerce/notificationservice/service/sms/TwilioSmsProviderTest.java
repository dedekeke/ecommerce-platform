package com.ecommerce.notificationservice.service.sms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TwilioSmsProvider}. The Twilio SDK is mocked at the
 * {@link TwilioMessageClient} boundary so no real credentials or network calls are involved.
 */
class TwilioSmsProviderTest {

    private TwilioMessageClient messageClient;
    private TwilioSmsProvider provider;

    @BeforeEach
    void setUp() {
        messageClient = mock(TwilioMessageClient.class);
        provider = new TwilioSmsProvider(messageClient);
    }

    @Test
    void should_delegateToTwilioClient_when_sending() {
        when(messageClient.sendMessage(anyString(), anyString())).thenReturn("SM123");

        assertThatCode(() -> provider.send("+1234567890", "hello"))
                .doesNotThrowAnyException();

        verify(messageClient).sendMessage(eq("+1234567890"), eq("hello"));
    }

    @Test
    void should_wrapInSmsDeliveryException_when_twilioClientFails() {
        when(messageClient.sendMessage(anyString(), anyString()))
                .thenThrow(new RuntimeException("Twilio API error"));

        assertThatThrownBy(() -> provider.send("+1234567890", "hello"))
                .isInstanceOf(SmsDeliveryException.class)
                .hasMessageContaining("Twilio")
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
