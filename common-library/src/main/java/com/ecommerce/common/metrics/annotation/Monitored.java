package com.ecommerce.common.metrics.annotation;

import io.micrometer.core.annotation.Timed;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for monitoring method execution.
 * Automatically records execution time, success/failure counts, and exceptions.
 *
 * This is a convenience annotation that combines @Timed with custom metric recording.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Timed
public @interface Monitored {

    /**
     * Metric name. If not specified, derived from class and method name.
     */
    String value() default "";

    /**
     * Metric description.
     */
    String description() default "";

    /**
     * Whether to record percentiles (p50, p95, p99).
     */
    boolean percentiles() default true;

    /**
     * Whether to record histogram for Prometheus.
     */
    boolean histogram() default true;

    /**
     * Extra tags to add to the metric.
     */
    String[] extraTags() default {};
}
