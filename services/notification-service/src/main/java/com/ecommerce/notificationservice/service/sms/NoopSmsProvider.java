package com.ecommerce.notificationservice.service.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default SMS provider: logs the message and returns. Active when {@code notification.sms.provider}
 * is {@code noop} or unset, so no Twilio credentials are ever required for tests or local dev.
 * Preserves the historical "[MOCK SMS] Would send..." behaviour.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "notification.sms.provider", havingValue = "noop", matchIfMissing = true)
public class NoopSmsProvider implements SmsProvider {

    @Override
    public void send(String phoneNumber, String message) {
        log.info("[noop-sms] Would send SMS to {}: {}", phoneNumber, message);
    }
}
