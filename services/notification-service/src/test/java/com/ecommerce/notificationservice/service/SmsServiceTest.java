package com.ecommerce.notificationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class for SmsService
 * Following TDD principles
 */
class SmsServiceTest {

    private SmsService smsService;

    @BeforeEach
    void setUp() {
        smsService = new SmsService();
        ReflectionTestUtils.setField(smsService, "smsEnabled", false); // Mock mode by default
        ReflectionTestUtils.setField(smsService, "smsProvider", "twilio");
    }

    @Test
    void shouldSendSmsInMockMode() {
        // When
        smsService.sendSms("+1234567890", "Test message");

        // Then
        // Should log but not throw exception
        // Verification happens through logs (manual check or log capture)
    }

    @Test
    void shouldSendSmsWhenEnabled() {
        // Given
        ReflectionTestUtils.setField(smsService, "smsEnabled", true);

        // When
        smsService.sendSms("+1234567890", "Test message");

        // Then
        // Should simulate sending (manual check or log capture)
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "+11234567890",
            "+442071234567",
            "+61412345678",
            "+8613800138000"
    })
    void shouldValidateCorrectPhoneNumberFormat(String phoneNumber) {
        // When
        boolean result = smsService.isValidPhoneNumber(phoneNumber);

        // Then
        assertThat(result).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1234567890",        // Missing +
            "+1",                // Too short
            "+123456789012345",  // Too long (16 digits)
            "",                  // Empty
            "+0123456789",       // Starts with 0
            "abc",               // Non-numeric
            "+1-234-567-890"     // Contains dashes
    })
    void shouldRejectInvalidPhoneNumberFormat(String phoneNumber) {
        // When
        boolean result = smsService.isValidPhoneNumber(phoneNumber);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void shouldRejectNullPhoneNumber() {
        // When
        boolean result = smsService.isValidPhoneNumber(null);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void shouldHandleLongMessage() {
        // Given
        String longMessage = "This is a very long message that exceeds the typical SMS character limit. "
                + "SMS messages are typically limited to 160 characters for standard text, "
                + "and longer messages are split into multiple parts.";

        // When
        smsService.sendSms("+1234567890", longMessage);

        // Then
        // Should handle without throwing exception
    }

    @Test
    void shouldHandleSpecialCharacters() {
        // Given
        String messageWithSpecialChars = "Hello! Your order #12345 is ready. Cost: $99.99 🎉";

        // When
        smsService.sendSms("+1234567890", messageWithSpecialChars);

        // Then
        // Should handle without throwing exception
    }
}
