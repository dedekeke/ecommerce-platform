package com.ecommerce.notificationservice.service.push;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the env-gated {@link PushProvider} selection: {@code noop} by default, {@code fcm} when
 * configured. Symmetric with the SMS and payment provider selection.
 */
class PushProviderSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(NoopPushProvider.class, FcmPushProvider.class, StubClientConfig.class);

    @Test
    void should_selectNoopProvider_when_providerUnset() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(PushProvider.class);
            assertThat(context).hasSingleBean(NoopPushProvider.class);
            assertThat(context).doesNotHaveBean(FcmPushProvider.class);
        });
    }

    @Test
    void should_selectFcmProvider_when_providerIsFcm() {
        runner.withPropertyValues("notification.push.provider=fcm").run(context -> {
            assertThat(context).hasSingleBean(PushProvider.class);
            assertThat(context).hasSingleBean(FcmPushProvider.class);
            assertThat(context).doesNotHaveBean(NoopPushProvider.class);
        });
    }

    @Configuration
    static class StubClientConfig {
        @Bean
        FcmMessageClient fcmMessageClient() {
            return mock(FcmMessageClient.class);
        }
    }
}
