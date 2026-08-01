package com.ecommerce.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Filter to add security headers to all responses.
 *
 * Implements OWASP recommended security headers:
 * - X-Frame-Options: Prevents clickjacking attacks
 * - X-Content-Type-Options: Prevents MIME type sniffing
 * - X-XSS-Protection: Enables browser XSS filtering
 * - Strict-Transport-Security: Enforces HTTPS (HSTS)
 * - Content-Security-Policy: Controls resource loading
 * - Referrer-Policy: Controls referrer information
 * - Permissions-Policy: Controls browser features
 * - Cache-Control: Prevents caching of sensitive responses
 */
@Slf4j
@Component
public class SecurityHeadersFilter implements WebFilter, Ordered {

    @Value("${security.headers.hsts.enabled:true}")
    private boolean hstsEnabled;

    @Value("${security.headers.hsts.max-age:31536000}")
    private long hstsMaxAge;

    @Value("${security.headers.csp.enabled:true}")
    private boolean cspEnabled;

    @Value("${security.headers.frame-options:DENY}")
    private String frameOptions;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Register the header writer as a beforeCommit hook instead of running it
        // after chain.filter() completes. By the time the downstream chain (or the
        // security filter chain emitting a 401) finishes, the response is already
        // committed and its Netty headers are read-only — mutating them throws
        // UnsupportedOperationException, which Netty then reports as a 500. The
        // beforeCommit callback fires while the headers are still writable, so it
        // works for both 2xx and short-circuited (e.g. 401) responses.
        exchange.getResponse().beforeCommit(() -> {
            addSecurityHeaders(exchange);
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    private void addSecurityHeaders(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getResponse().getHeaders();

        // X-Frame-Options - Prevent clickjacking
        if (!headers.containsKey("X-Frame-Options")) {
            headers.add("X-Frame-Options", frameOptions);
        }

        // X-Content-Type-Options - Prevent MIME sniffing
        if (!headers.containsKey("X-Content-Type-Options")) {
            headers.add("X-Content-Type-Options", "nosniff");
        }

        // X-XSS-Protection - Enable XSS filter (legacy, but still useful)
        if (!headers.containsKey("X-XSS-Protection")) {
            headers.add("X-XSS-Protection", "1; mode=block");
        }

        // Strict-Transport-Security - HSTS
        if (hstsEnabled && !headers.containsKey("Strict-Transport-Security")) {
            headers.add("Strict-Transport-Security",
                String.format("max-age=%d; includeSubDomains; preload", hstsMaxAge));
        }

        // Referrer-Policy - Control referrer information
        if (!headers.containsKey("Referrer-Policy")) {
            headers.add("Referrer-Policy", "strict-origin-when-cross-origin");
        }

        // Permissions-Policy - Control browser features
        if (!headers.containsKey("Permissions-Policy")) {
            headers.add("Permissions-Policy",
                "geolocation=(), microphone=(), camera=(), payment=(self)");
        }

        // Content-Security-Policy - Control resource loading
        if (cspEnabled && !headers.containsKey("Content-Security-Policy")) {
            headers.add("Content-Security-Policy", buildCspHeader());
        }

        // Cache-Control for API responses
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/api/") && !headers.containsKey("Cache-Control")) {
            headers.add("Cache-Control", "no-store, no-cache, must-revalidate, private");
            headers.add("Pragma", "no-cache");
        }
    }

    private String buildCspHeader() {
        return String.join("; ",
            "default-src 'self'",
            "script-src 'self' 'unsafe-inline' 'unsafe-eval'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: https:",
            "font-src 'self' data:",
            "connect-src 'self' https:",
            "frame-ancestors 'none'",
            "base-uri 'self'",
            "form-action 'self'"
        );
    }
}
