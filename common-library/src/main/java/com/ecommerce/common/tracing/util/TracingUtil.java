package com.ecommerce.common.tracing.util;

import brave.Span;
import brave.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Utility class for creating custom spans for business operations.
 * Provides convenient methods to trace specific operations with proper error handling.
 */
@Component
public class TracingUtil {

    private static final Logger log = LoggerFactory.getLogger(TracingUtil.class);

    private final Tracer tracer;

    public TracingUtil(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Execute an operation within a custom span.
     *
     * @param spanName Name of the span
     * @param operation The operation to execute
     * @return Result of the operation
     */
    public <T> T traceOperation(String spanName, Supplier<T> operation) {
        Span span = tracer.nextSpan().name(spanName).start();
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            T result = operation.get();
            span.tag("status", "success");
            return result;
        } catch (Exception e) {
            span.tag("status", "error");
            span.tag("error", e.getClass().getSimpleName());
            span.tag("error.message", e.getMessage() != null ? e.getMessage() : "");
            log.error("Error in traced operation: {}", spanName, e);
            throw e;
        } finally {
            span.finish();
        }
    }

    /**
     * Execute a void operation within a custom span.
     *
     * @param spanName Name of the span
     * @param operation The operation to execute
     */
    public void traceVoidOperation(String spanName, Runnable operation) {
        Span span = tracer.nextSpan().name(spanName).start();
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            operation.run();
            span.tag("status", "success");
        } catch (Exception e) {
            span.tag("status", "error");
            span.tag("error", e.getClass().getSimpleName());
            span.tag("error.message", e.getMessage() != null ? e.getMessage() : "");
            log.error("Error in traced operation: {}", spanName, e);
            throw e;
        } finally {
            span.finish();
        }
    }

    /**
     * Add a tag to the current span.
     *
     * @param key Tag key
     * @param value Tag value
     */
    public void addTag(String key, String value) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.tag(key, value);
        }
    }

    /**
     * Add an annotation to the current span.
     *
     * @param message Annotation message
     */
    public void addAnnotation(String message) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.annotate(message);
        }
    }

    /**
     * Get the current trace ID.
     *
     * @return Current trace ID or null if no active span
     */
    public String getCurrentTraceId() {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            return currentSpan.context().traceIdString();
        }
        return null;
    }

    /**
     * Get the current span ID.
     *
     * @return Current span ID or null if no active span
     */
    public String getCurrentSpanId() {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            return currentSpan.context().spanIdString();
        }
        return null;
    }

    /**
     * Create a child span for database operations.
     *
     * @param operation Database operation name (e.g., "SELECT", "INSERT")
     * @param table Table name
     * @return New span (must be finished by caller)
     */
    public Span createDatabaseSpan(String operation, String table) {
        return tracer.nextSpan()
                .name("database." + operation.toLowerCase())
                .tag("db.operation", operation)
                .tag("db.table", table)
                .tag("span.kind", "client")
                .start();
    }

    /**
     * Create a child span for external API calls.
     *
     * @param serviceName Name of the external service
     * @param endpoint API endpoint
     * @return New span (must be finished by caller)
     */
    public Span createExternalCallSpan(String serviceName, String endpoint) {
        return tracer.nextSpan()
                .name("external." + serviceName)
                .tag("service.name", serviceName)
                .tag("http.endpoint", endpoint)
                .tag("span.kind", "client")
                .start();
    }

    /**
     * Create a child span for gRPC calls.
     *
     * @param serviceName gRPC service name
     * @param method gRPC method name
     * @return New span (must be finished by caller)
     */
    public Span createGrpcSpan(String serviceName, String method) {
        return tracer.nextSpan()
                .name("grpc." + serviceName + "/" + method)
                .tag("grpc.service", serviceName)
                .tag("grpc.method", method)
                .tag("span.kind", "client")
                .start();
    }

    /**
     * Create a child span for Kafka operations.
     *
     * @param operation Operation type (e.g., "produce", "consume")
     * @param topic Kafka topic
     * @return New span (must be finished by caller)
     */
    public Span createKafkaSpan(String operation, String topic) {
        return tracer.nextSpan()
                .name("kafka." + operation)
                .tag("messaging.operation", operation)
                .tag("messaging.destination", topic)
                .tag("messaging.system", "kafka")
                .tag("span.kind", "producer".equals(operation) ? "producer" : "consumer")
                .start();
    }
}
