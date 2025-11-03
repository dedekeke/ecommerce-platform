package com.ecommerce.common.tracing.filter;

import brave.baggage.BaggageField;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter to ensure correlation IDs are present in all requests.
 * Generates a new correlation ID if one is not provided.
 * Adds correlation ID to response headers for client tracking.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements Filter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String USER_ID_HEADER = "X-User-ID";

    private final BaggageField correlationIdField;
    private final BaggageField requestIdField;
    private final BaggageField userIdField;

    public CorrelationIdFilter(
            BaggageField correlationIdField,
            BaggageField requestIdField,
            BaggageField userIdField) {
        this.correlationIdField = correlationIdField;
        this.requestIdField = requestIdField;
        this.userIdField = userIdField;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        try {
            // Get or generate correlation ID
            String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = generateId();
            }

            // Get or generate request ID
            String requestId = httpRequest.getHeader(REQUEST_ID_HEADER);
            if (requestId == null || requestId.isBlank()) {
                requestId = generateId();
            }

            // Get user ID if present
            String userId = httpRequest.getHeader(USER_ID_HEADER);

            // Set baggage fields for propagation
            correlationIdField.updateValue(correlationId);
            requestIdField.updateValue(requestId);
            if (userId != null && !userId.isBlank()) {
                userIdField.updateValue(userId);
            }

            // Add to MDC for logging (backup in case baggage doesn't work)
            MDC.put("correlationId", correlationId);
            MDC.put("requestId", requestId);
            if (userId != null && !userId.isBlank()) {
                MDC.put("userId", userId);
            }

            // Add correlation ID to response headers
            httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
            httpResponse.setHeader(REQUEST_ID_HEADER, requestId);

            chain.doFilter(request, response);
        } finally {
            // Clean up MDC
            MDC.clear();
        }
    }

    private String generateId() {
        return UUID.randomUUID().toString();
    }
}
