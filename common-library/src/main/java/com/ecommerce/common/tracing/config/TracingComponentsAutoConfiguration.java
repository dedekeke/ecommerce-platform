package com.ecommerce.common.tracing.config;

import brave.Tracer;
import com.ecommerce.common.tracing.aspect.TracedAspect;
import com.ecommerce.common.tracing.util.TracingUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers tracing helper beans only when a {@link Tracer} is present. These
 * were previously {@code @Component}/{@code @Aspect} beans that broke sliced
 * tests (e.g. {@code @DataJpaTest}) which component-scan {@code com.ecommerce.common}
 * but provide no Tracer. Gating on the Tracer bean keeps them out of those slices
 * while still wiring them in every running service.
 */
@AutoConfiguration
@ConditionalOnClass(Tracer.class)
public class TracingComponentsAutoConfiguration {

    @Bean
    @ConditionalOnBean(Tracer.class)
    @ConditionalOnMissingBean(TracingUtil.class)
    public TracingUtil tracingUtil(Tracer tracer) {
        return new TracingUtil(tracer);
    }

    @Bean
    @ConditionalOnBean(Tracer.class)
    @ConditionalOnMissingBean(TracedAspect.class)
    public TracedAspect tracedAspect(Tracer tracer) {
        return new TracedAspect(tracer);
    }
}
