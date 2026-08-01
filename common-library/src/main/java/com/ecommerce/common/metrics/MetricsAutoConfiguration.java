package com.ecommerce.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link BusinessMetrics} only when a {@link MeterRegistry} is on the
 * context. This keeps the metrics bean out of sliced tests (e.g. JPA slices)
 * that have no MeterRegistry, while still providing it for every running service
 * (Actuator/Micrometer supplies the registry).
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
public class MetricsAutoConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(BusinessMetrics.class)
    public BusinessMetrics businessMetrics(MeterRegistry registry) {
        return new BusinessMetrics(registry);
    }
}
