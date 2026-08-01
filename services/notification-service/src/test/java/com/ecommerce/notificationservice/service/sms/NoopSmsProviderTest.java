package com.ecommerce.notificationservice.service.sms;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class NoopSmsProviderTest {

    private final NoopSmsProvider provider = new NoopSmsProvider();

    @Test
    void should_notThrow_when_sending() {
        assertThatCode(() -> provider.send("+1234567890", "hello"))
                .doesNotThrowAnyException();
    }
}
