package com.ecommerce.common.metrics.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Central metrics configuration for all microservices.
 * Configures common tags, filters, and metric naming conventions.
 */
@Configuration
public class MetricsConfig {

    @Value("${spring.application.name:unknown}")
    private String applicationName;

    @Value("${spring.profiles.active:default}")
    private String profile;

    /**
     * Add common tags to all metrics for consistent filtering in Grafana.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags(
                        "application", applicationName,
                        "environment", profile,
                        "instance", getInstanceId()
                )
                .meterFilter(MeterFilter.deny(id -> {
                    // Deny noisy metrics that we don't need
                    String name = id.getName();
                    return name.startsWith("jvm.threads.") ||
                           name.startsWith("process.") ||
                           name.startsWith("system.load.average");
                }));
    }

    /**
     * Enable @Timed annotation support for custom timing metrics.
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    /**
     * Get instance ID (hostname or container ID).
     */
    private String getInstanceId() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
