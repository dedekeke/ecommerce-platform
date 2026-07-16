package com.ecommerce.notificationservice.service;

import com.ecommerce.notificationservice.service.sms.SmsDeliveryException;
import com.ecommerce.notificationservice.service.sms.SmsProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link SmsService}. The service is a thin delegator over the configured
 * {@link SmsProvider}; the provider selection itself is covered by SmsProviderSelectionTest.
 */
class SmsServiceTest {

    private SmsProvider smsProvider;
    private SmsService smsService;

    @BeforeEach
    void setUp() {
        smsProvider = mock(SmsProvider.class);
        smsService = new SmsService(smsProvider);
    }

    @Test
    void should_delegateToProvider_when_sendingSms() {
        smsService.sendSms("+1234567890", "Test message");

        verify(smsProvider).send(eq("+1234567890"), eq("Test message"));
    }

    @Test
    void should_propagateDeliveryException_when_providerFails() {
        doThrow(new SmsDeliveryException("gateway down", new RuntimeException()))
                .when(smsProvider).send(Mockito.anyString(), Mockito.anyString());

        assertThatThrownBy(() -> smsService.sendSms("+1234567890", "Test message"))
                .isInstanceOf(SmsDeliveryException.class);
    }

    @Test
    void should_passLongMessageThrough_when_bodyExceedsSingleSegment() {
        String longMessage = "This is a very long message that exceeds the typical SMS character limit. "
                + "SMS messages are typically limited to 160 characters for standard text, "
                + "and longer messages are split into multiple parts.";

        smsService.sendSms("+1234567890", longMessage);

        verify(smsProvider).send(eq("+1234567890"), eq(longMessage));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "+11234567890",
            "+442071234567",
            "+61412345678",
            "+8613800138000"
    })
    void should_returnTrue_when_phoneNumberIsValidE164(String phoneNumber) {
        assertThat(smsService.isValidPhoneNumber(phoneNumber)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1234567890",        // Missing +
            "+1",                // Too short
            "+1234567890123456", // Too long (16 digits) - exceeds E.164 max of 15
            "",                  // Empty
            "+0123456789",       // Starts with 0
            "abc",               // Non-numeric
            "+1-234-567-890"     // Contains dashes
    })
    void should_returnFalse_when_phoneNumberIsInvalid(String phoneNumber) {
        assertThat(smsService.isValidPhoneNumber(phoneNumber)).isFalse();
    }

    @Test
    void should_returnFalse_when_phoneNumberIsNull() {
        assertThat(smsService.isValidPhoneNumber(null)).isFalse();
    }
}
