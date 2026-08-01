package com.ecommerce.notificationservice.service.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Real SMS provider backed by Twilio (activated when {@code notification.sms.provider=twilio}).
 *
 * <p>Delegates the actual SDK call to {@link TwilioMessageClient} and translates any SDK failure
 * into a {@link SmsDeliveryException} so the notification is marked FAILED/RETRYING rather than
 * silently lost. Credentials live in {@code TwilioProperties} (env-only) and never reach this class.</p>
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "notification.sms.provider", havingValue = "twilio")
public class TwilioSmsProvider implements SmsProvider {

    private final TwilioMessageClient messageClient;

    public TwilioSmsProvider(TwilioMessageClient messageClient) {
        this.messageClient = messageClient;
    }

    @Override
    public void send(String phoneNumber, String message) {
        try {
            String sid = messageClient.sendMessage(phoneNumber, message);
            log.info("[twilio] SMS sent to {} (sid={})", phoneNumber, sid);
        } catch (RuntimeException e) {
            log.error("[twilio] Failed to send SMS to {}: {}", phoneNumber, e.getMessage());
            throw new SmsDeliveryException("Failed to send SMS via Twilio", e);
        }
    }
}
