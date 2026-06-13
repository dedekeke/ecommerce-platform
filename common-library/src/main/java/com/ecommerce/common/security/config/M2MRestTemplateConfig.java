package com.ecommerce.common.security.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for RestTemplate used in M2M authentication. Only active when
 * Auth0 M2M is configured ({@code auth0.m2m.client-id} present), so services and
 * sliced tests without M2M don't pull a RestTemplateBuilder they don't have.
 */
@Configuration
@ConditionalOnProperty(prefix = "auth0.m2m", name = "client-id")
public class M2MRestTemplateConfig {

    /**
     * Creates a RestTemplate bean for M2M authentication requests.
     * Configured with appropriate timeouts.
     *
     * @param builder RestTemplateBuilder
     * @return configured RestTemplate
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
}
