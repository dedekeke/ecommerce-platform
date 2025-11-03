package com.ecommerce.common.tracing.aspect;

import brave.Span;
import brave.Tracer;
import com.ecommerce.common.tracing.annotation.Traced;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Aspect to handle @Traced annotation.
 * Automatically creates custom spans for annotated methods.
 */
@Aspect
@Component
public class TracedAspect {

    private static final Logger log = LoggerFactory.getLogger(TracedAspect.class);

    private final Tracer tracer;

    public TracedAspect(Tracer tracer) {
        this.tracer = tracer;
    }

    @Around("@annotation(traced)")
    public Object traceMethod(ProceedingJoinPoint joinPoint, Traced traced) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getMethod().getName();
        String className = signature.getDeclaringType().getSimpleName();

        // Determine span name
        String spanName = traced.value().isEmpty()
                ? className + "." + methodName
                : traced.value();

        Span span = tracer.nextSpan().name(spanName).start();

        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            // Add operation type tag
            span.tag("operation.type", traced.operation());
            span.tag("class", className);
            span.tag("method", methodName);

            // Add parameter tags if enabled
            if (traced.includeParameters() && joinPoint.getArgs().length > 0) {
                String[] paramNames = signature.getParameterNames();
                Object[] paramValues = joinPoint.getArgs();
                for (int i = 0; i < paramNames.length && i < paramValues.length; i++) {
                    if (paramValues[i] != null) {
                        span.tag("param." + paramNames[i], sanitizeValue(paramValues[i]));
                    }
                }
            }

            // Execute the method
            Object result = joinPoint.proceed();

            // Add return value tag if enabled
            if (traced.includeReturnValue() && result != null) {
                span.tag("return.value", sanitizeValue(result));
            }

            span.tag("status", "success");
            return result;

        } catch (Throwable e) {
            span.tag("status", "error");
            span.tag("error", e.getClass().getSimpleName());
            span.tag("error.message", e.getMessage() != null ? e.getMessage() : "");
            log.error("Error in traced method {}.{}", className, methodName, e);
            throw e;
        } finally {
            span.finish();
        }
    }

    /**
     * Sanitize value for span tags (limit size and remove sensitive data).
     */
    private String sanitizeValue(Object value) {
        if (value == null) {
            return "null";
        }

        String stringValue = value.toString();

        // Limit length to avoid huge span tags
        if (stringValue.length() > 100) {
            stringValue = stringValue.substring(0, 100) + "...";
        }

        // Remove potential sensitive data patterns
        stringValue = stringValue.replaceAll("(?i)password=\\S+", "password=***");
        stringValue = stringValue.replaceAll("(?i)token=\\S+", "token=***");
        stringValue = stringValue.replaceAll("(?i)secret=\\S+", "secret=***");

        return stringValue;
    }
}
