package com.ecommerce.notificationservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Twilio credentials for the SMS gateway.
 *
 * <p>All three values are sourced from environment variables ({@code TWILIO_ACCOUNT_SID},
 * {@code TWILIO_AUTH_TOKEN}, {@code TWILIO_FROM_NUMBER}) via placeholders in {@code application.yml}
 * and are never hardcoded or committed. They are only required when
 * {@code notification.sms.provider=twilio}; under the default {@code noop} provider they stay blank.</p>
 */
@Component
@ConfigurationProperties(prefix = "notification.sms.twilio")
@Getter
@Setter
public class TwilioProperties {

    private String accountSid;

    private String authToken;

    private String fromNumber;
}
