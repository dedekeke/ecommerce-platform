package com.ecommerce.notificationservice.service.sms;

import com.ecommerce.notificationservice.config.TwilioProperties;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Real {@link TwilioMessageClient} backed by the Twilio Java SDK. Instantiated only when
 * {@code notification.sms.provider=twilio}; the account SID / auth token are read once from
 * {@link TwilioProperties} (env-only) to initialise the SDK's static credential holder.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "notification.sms.provider", havingValue = "twilio")
public class DefaultTwilioMessageClient implements TwilioMessageClient {

    private final TwilioProperties properties;

    public DefaultTwilioMessageClient(TwilioProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        Twilio.init(properties.getAccountSid(), properties.getAuthToken());
        log.info("Twilio SMS client initialised (from-number={})", properties.getFromNumber());
    }

    @Override
    public String sendMessage(String toPhoneNumber, String body) {
        Message message = Message.creator(
                new PhoneNumber(toPhoneNumber),
                new PhoneNumber(properties.getFromNumber()),
                body).create();
        return message.getSid();
    }
}
