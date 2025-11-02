package com.ecommerce.common.tracing.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to automatically create a custom span for a method.
 * The span name will be derived from the method name or can be specified explicitly.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Traced {

    /**
     * Optional custom name for the span.
     * If not specified, the method name will be used.
     */
    String value() default "";

    /**
     * Optional operation type (e.g., "database", "external-api", "business-logic").
     */
    String operation() default "business-logic";

    /**
     * Whether to include method parameters as span tags.
     */
    boolean includeParameters() default false;

    /**
     * Whether to include the return value as a span tag.
     */
    boolean includeReturnValue() default false;
}
