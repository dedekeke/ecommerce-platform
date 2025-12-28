package com.ecommerce.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Filter to customize rate limit exceeded responses.
 *
 * When a request is rate limited (HTTP 429), this filter intercepts the response
 * and returns a structured JSON error message with retry information.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitExceededHandler implements WebFilter, Ordered {

    private final ObjectMapper objectMapper;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange)
            .then(Mono.defer(() -> {
                if (exchange.getResponse().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    return handleRateLimitExceeded(exchange);
                }
                return Mono.empty();
            }));
    }

    private Mono<Void> handleRateLimitExceeded(ServerWebExchange exchange) {
        var response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.empty();
        }

        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String retryAfter = response.getHeaders().getFirst("X-RateLimit-Retry-After-Seconds");
        String remaining = response.getHeaders().getFirst("X-RateLimit-Remaining");
        String limit = response.getHeaders().getFirst("X-RateLimit-Burst-Capacity");

        Map<String, Object> errorBody = Map.of(
            "timestamp", Instant.now().toString(),
            "status", 429,
            "error", "Too Many Requests",
            "message", "Rate limit exceeded. Please slow down your requests.",
            "path", exchange.getRequest().getPath().value(),
            "retryAfterSeconds", retryAfter != null ? Integer.parseInt(retryAfter) : 1,
            "rateLimitInfo", Map.of(
                "remaining", remaining != null ? remaining : "0",
                "limit", limit != null ? limit : "unknown"
            )
        );

        String clientIp = getClientIp(exchange);
        log.warn("Rate limit exceeded for client: {} on path: {}",
            clientIp, exchange.getRequest().getPath().value());

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorBody);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize rate limit response", e);
            return Mono.empty();
        }
    }

    private String getClientIp(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        var remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }
}
