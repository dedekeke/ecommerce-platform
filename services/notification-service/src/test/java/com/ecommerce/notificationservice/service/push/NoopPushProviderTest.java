package com.ecommerce.notificationservice.service.push;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;

class NoopPushProviderTest {

    private final NoopPushProvider provider = new NoopPushProvider();

    @Test
    void should_notThrow_when_sending() {
        assertThatCode(() -> provider.send("token123", "Title", "Body", Map.of("k", "v")))
                .doesNotThrowAnyException();
    }
}
