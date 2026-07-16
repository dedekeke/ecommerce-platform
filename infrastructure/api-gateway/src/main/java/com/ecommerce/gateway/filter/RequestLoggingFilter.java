package com.ecommerce.gateway.filter;

import com.ecommerce.gateway.config.ClientIpResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Filter for logging HTTP requests and responses.
 *
 * Features:
 * - Logs request method, path, client IP, user principal
 * - Logs response status and duration
 * - Excludes sensitive headers from logs (Authorization, Cookie, etc.)
 * - Adds correlation ID for request tracing
 * - Configurable logging level
 */
@Slf4j
@Component
public class RequestLoggingFilter implements WebFilter, Ordered {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String START_TIME_ATTR = "requestStartTime";

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
        "authorization",
        "cookie",
        "set-cookie",
        "x-api-key",
        "x-auth-token",
        "x-csrf-token",
        "proxy-authorization"
    );

    private static final Set<String> SENSITIVE_PARAMS = Set.of(
        "password",
        "token",
        "secret",
        "key",
        "credential",
        "auth"
    );

    @Value("${logging.request.enabled:true}")
    private boolean loggingEnabled;

    @Value("${logging.request.include-headers:false}")
    private boolean includeHeaders;

    @Value("${logging.request.include-query-params:true}")
    private boolean includeQueryParams;

    /** Shared, spoofing-resistant client-IP resolver (see {@link ClientIpResolver}). */
    private final ClientIpResolver clientIpResolver;

    public RequestLoggingFilter(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!loggingEnabled) {
            return chain.filter(exchange);
        }

        String correlationId = getOrCreateCorrelationId(exchange);
        Instant startTime = Instant.now();

        ServerWebExchange mutatedExchange = exchange.mutate()
            .request(r -> r.header(CORRELATION_ID_HEADER, correlationId))
            .build();

        mutatedExchange.getAttributes().put(START_TIME_ATTR, startTime);

        return mutatedExchange.getPrincipal()
            .map(Principal::getName)
            .defaultIfEmpty("anonymous")
            .doOnNext(principal -> logRequest(mutatedExchange, correlationId, principal))
            .then(chain.filter(mutatedExchange))
            .doFinally(signalType -> logResponse(mutatedExchange, correlationId, startTime));
    }

    private String getOrCreateCorrelationId(ServerWebExchange exchange) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString().substring(0, 8);
        }
        return correlationId;
    }

    private void logRequest(ServerWebExchange exchange, String correlationId, String principal) {
        ServerHttpRequest request = exchange.getRequest();

        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("REQUEST [").append(correlationId).append("] ");
        logBuilder.append(request.getMethod()).append(" ");
        logBuilder.append(request.getPath().value());

        if (includeQueryParams && request.getQueryParams() != null && !request.getQueryParams().isEmpty()) {
            logBuilder.append(" params=").append(sanitizeQueryParams(request));
        }

        logBuilder.append(" client=").append(clientIpResolver.resolve(exchange));
        logBuilder.append(" user=").append(principal);

        if (includeHeaders) {
            logBuilder.append(" headers=").append(sanitizeHeaders(request.getHeaders()));
        }

        log.info(logBuilder.toString());
    }

    private void logResponse(ServerWebExchange exchange, String correlationId, Instant startTime) {
        ServerHttpResponse response = exchange.getResponse();
        Duration duration = Duration.between(startTime, Instant.now());

        String statusCode = response.getStatusCode() != null
            ? String.valueOf(response.getStatusCode().value())
            : "unknown";

        log.info("RESPONSE [{}] status={} duration={}ms path={}",
            correlationId,
            statusCode,
            duration.toMillis(),
            exchange.getRequest().getPath().value()
        );
    }

    private String sanitizeHeaders(HttpHeaders headers) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : headers.entrySet()) {
            String headerName = entry.getKey().toLowerCase();
            if (SENSITIVE_HEADERS.contains(headerName)) {
                continue;
            }
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append("=");
            List<String> values = entry.getValue();
            if (values.size() == 1) {
                sb.append(values.get(0));
            } else {
                sb.append(values);
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String sanitizeQueryParams(ServerHttpRequest request) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : request.getQueryParams().entrySet()) {
            String paramName = entry.getKey().toLowerCase();
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append("=");
            if (SENSITIVE_PARAMS.stream().anyMatch(paramName::contains)) {
                sb.append("[REDACTED]");
            } else {
                List<String> values = entry.getValue();
                if (values.size() == 1) {
                    sb.append(values.get(0));
                } else {
                    sb.append(values);
                }
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
