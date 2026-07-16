package com.ecommerce.notificationservice.service.sms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the env-gated {@link SmsProvider} selection (mirrors the payment-service
 * PAYMENT_PROVIDER pattern): {@code noop} by default, {@code twilio} when configured.
 */
class SmsProviderSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(NoopSmsProvider.class, TwilioSmsProvider.class, StubClientConfig.class);

    @Test
    void should_selectNoopProvider_when_providerUnset() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(SmsProvider.class);
            assertThat(context).hasSingleBean(NoopSmsProvider.class);
            assertThat(context).doesNotHaveBean(TwilioSmsProvider.class);
        });
    }

    @Test
    void should_selectNoopProvider_when_providerIsNoop() {
        runner.withPropertyValues("notification.sms.provider=noop").run(context -> {
            assertThat(context).hasSingleBean(NoopSmsProvider.class);
            assertThat(context).doesNotHaveBean(TwilioSmsProvider.class);
        });
    }

    @Test
    void should_selectTwilioProvider_when_providerIsTwilio() {
        runner.withPropertyValues("notification.sms.provider=twilio").run(context -> {
            assertThat(context).hasSingleBean(SmsProvider.class);
            assertThat(context).hasSingleBean(TwilioSmsProvider.class);
            assertThat(context).doesNotHaveBean(NoopSmsProvider.class);
        });
    }

    @Configuration
    static class StubClientConfig {
        @Bean
        TwilioMessageClient twilioMessageClient() {
            return mock(TwilioMessageClient.class);
        }
    }
}
