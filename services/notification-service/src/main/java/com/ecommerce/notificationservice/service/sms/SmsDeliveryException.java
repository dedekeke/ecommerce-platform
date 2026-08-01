package com.ecommerce.notificationservice.service.sms;

/**
 * Raised when an {@link SmsProvider} fails to deliver a message. Unchecked so it propagates to
 * {@code NotificationService}, which marks the notification FAILED/RETRYING for the retry scheduler.
 */
public class SmsDeliveryException extends RuntimeException {

    public SmsDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
