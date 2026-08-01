package com.ecommerce.common.featureflag;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Spring auto-configuration that registers a default {@link FeatureFlags}
 * bean for every service on the classpath. Services that need a different
 * implementation (e.g. an Unleash-backed one in production) can simply
 * declare their own {@code @Bean FeatureFlags} and this one will back off
 * via {@link ConditionalOnMissingBean}.
 */
@AutoConfiguration
public class FeatureFlagAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FeatureFlags.class)
    public FeatureFlags featureFlags(Environment environment) {
        return new EnvVarFeatureFlags(environment);
    }
}
