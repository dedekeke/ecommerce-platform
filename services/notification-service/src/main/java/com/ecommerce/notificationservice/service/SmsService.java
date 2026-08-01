package com.ecommerce.notificationservice.service;

import com.ecommerce.notificationservice.service.sms.SmsProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * SMS Service
 *
 * <p>Thin channel service that delegates to the configured {@link SmsProvider}
 * ({@code notification.sms.provider}: {@code noop} default, {@code twilio} real). A provider
 * delivery failure surfaces as an {@code SmsDeliveryException}, which {@code NotificationService}
 * catches to mark the notification FAILED/RETRYING — the message is never silently lost.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SmsService {

    private final SmsProvider smsProvider;

    /**
     * Send an SMS via the configured provider.
     */
    public void sendSms(String phoneNumber, String message) {
        smsProvider.send(phoneNumber, message);
    }

    /**
     * Validate phone number format (E.164: leading + and 10-15 digits).
     */
    public boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }
        return phoneNumber.matches("^\\+[1-9]\\d{9,14}$");
    }
}
