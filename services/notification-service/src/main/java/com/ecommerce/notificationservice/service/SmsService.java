package com.ecommerce.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * SMS Service
 * Mock implementation for Twilio SMS service
 */
@Service
@Slf4j
public class SmsService {

    @Value("${notification.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${notification.sms.provider:twilio}")
    private String smsProvider;

    /**
     * Send SMS message
     */
    public void sendSms(String phoneNumber, String message) {
        if (!smsEnabled) {
            log.info("[MOCK SMS] Would send to {}: {}", phoneNumber, message);
            return;
        }

        // TODO: Integrate with actual SMS provider (Twilio, AWS SNS, etc.)
        log.info("[{}] Sending SMS to {}: {}", smsProvider.toUpperCase(), phoneNumber, message);

        // Simulate SMS sending
        try {
            // In real implementation, call Twilio API:
            // twilioClient.messages.create(phoneNumber, fromNumber, message);
            Thread.sleep(100); // Simulate API call
            log.info("SMS sent successfully to: {}", phoneNumber);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to send SMS to: {}", phoneNumber, e);
            throw new RuntimeException("Failed to send SMS", e);
        }
    }

    /**
     * Validate phone number format
     */
    public boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }
        // Basic validation - starts with + and contains 10-15 digits
        return phoneNumber.matches("^\\+[1-9]\\d{9,14}$");
    }
}
