package com.ecommerce.notificationservice.service.push;

/**
 * Raised when a {@link PushProvider} fails to deliver a message. Unchecked so it propagates to
 * {@code NotificationService}, which marks the notification FAILED/RETRYING for the retry scheduler.
 */
public class PushDeliveryException extends RuntimeException {

    public PushDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
