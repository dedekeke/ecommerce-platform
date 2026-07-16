package com.ecommerce.notificationservice.service.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Default push provider: logs the notification and returns. Active when
 * {@code notification.push.provider} is {@code noop} or unset, so no Firebase credentials are ever
 * required for tests or local dev.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "notification.push.provider", havingValue = "noop", matchIfMissing = true)
public class NoopPushProvider implements PushProvider {

    @Override
    public void send(String deviceToken, String title, String body, Map<String, Object> data) {
        log.info("[noop-push] Would send push to {} — title='{}', body='{}'", deviceToken, title, body);
    }
}
