package com.ecommerce.common.tracing.config;

import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig;
import brave.baggage.CorrelationScopeConfig;
import brave.context.slf4j.MDCScopeDecorator;
import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext;
import com.ecommerce.common.tracing.filter.CorrelationIdFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for distributed tracing with Zipkin.
 * Enables OpenTelemetry/Brave tracing with correlation ID propagation through MDC.
 */
@Configuration
@ConditionalOnProperty(value = "management.tracing.enabled", havingValue = "true", matchIfMissing = true)
public class TracingConfig {

    /**
     * Configure CurrentTraceContext with MDC support for correlation IDs.
     * This ensures trace and span IDs are automatically added to logs.
     */
    @Bean
    public CurrentTraceContext.ScopeDecorator mdcScopeDecorator() {
        return MDCScopeDecorator.newBuilder()
                .clear()
                .build();
    }

    /**
     * Configure baggage fields for correlation ID propagation.
     * This allows custom fields to be propagated across service boundaries.
     */
    @Bean
    public BaggageField correlationIdField() {
        return BaggageField.create("correlation-id");
    }

    @Bean
    public BaggageField userIdField() {
        return BaggageField.create("user-id");
    }

    @Bean
    public BaggageField requestIdField() {
        return BaggageField.create("request-id");
    }

    /**
     * Configure baggage propagation to include custom fields in traces and MDC.
     */
    @Bean
    public BaggagePropagation.FactoryBuilder baggagePropagationFactoryBuilder(
            BaggageField correlationIdField,
            BaggageField userIdField,
            BaggageField requestIdField) {

        return BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
                .add(BaggagePropagationConfig.SingleBaggageField.newBuilder(correlationIdField)
                        .addKeyName("X-Correlation-ID")
                        .addKeyName("correlation-id")
                        .build())
                .add(BaggagePropagationConfig.SingleBaggageField.newBuilder(userIdField)
                        .addKeyName("X-User-ID")
                        .addKeyName("user-id")
                        .build())
                .add(BaggagePropagationConfig.SingleBaggageField.newBuilder(requestIdField)
                        .addKeyName("X-Request-ID")
                        .addKeyName("request-id")
                        .build());
    }

    /**
     * Configure correlation scope to add baggage fields to MDC for logging.
     */
    @Bean
    public CorrelationScopeConfig.SingleCorrelationField correlationIdMdcField(
            BaggageField correlationIdField) {
        return CorrelationScopeConfig.SingleCorrelationField.newBuilder(correlationIdField)
                .name("correlationId")
                .flushOnUpdate()
                .build();
    }

    @Bean
    public CorrelationScopeConfig.SingleCorrelationField userIdMdcField(
            BaggageField userIdField) {
        return CorrelationScopeConfig.SingleCorrelationField.newBuilder(userIdField)
                .name("userId")
                .flushOnUpdate()
                .build();
    }

    @Bean
    public CorrelationScopeConfig.SingleCorrelationField requestIdMdcField(
            BaggageField requestIdField) {
        return CorrelationScopeConfig.SingleCorrelationField.newBuilder(requestIdField)
                .name("requestId")
                .flushOnUpdate()
                .build();
    }

    /**
     * Servlet filter that stamps correlation/request/user IDs onto the baggage
     * fields and MDC. Registered here so it shares this config's lifecycle and
     * its {@link BaggageField} dependencies.
     */
    @Bean
    public CorrelationIdFilter correlationIdFilter(
            BaggageField correlationIdField,
            BaggageField requestIdField,
            BaggageField userIdField) {
        return new CorrelationIdFilter(correlationIdField, requestIdField, userIdField);
    }
}
