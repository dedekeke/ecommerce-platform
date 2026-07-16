package com.ecommerce.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.security.Principal;

/**
 * Rate Limiter Configuration for API Gateway.
 *
 * Provides different KeyResolver strategies for rate limiting:
 * - User-based: Rate limit per authenticated user
 * - IP-based: Rate limit per client IP address
 * - Combined: Use user ID if authenticated, otherwise use IP
 *
 * <p>All IP-based keys are derived via {@link ClientIpResolver}, which only
 * honors {@code X-Forwarded-For} from configured trusted proxies — so the
 * unauthenticated guest endpoints cannot be spoofed into per-request buckets.</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(TrustedProxyProperties.class)
public class RateLimiterConfig {

    private final ClientIpResolver clientIpResolver;

    /**
     * Primary key resolver that uses user ID for authenticated requests
     * and client IP for anonymous requests.
     *
     * This provides fair rate limiting:
     * - Authenticated users get their own quota
     * - Anonymous users are limited by IP
     */
    @Bean
    @Primary
    public KeyResolver combinedKeyResolver() {
        return exchange -> exchange.getPrincipal()
            .map(Principal::getName)
            .defaultIfEmpty(getClientIp(exchange))
            .doOnNext(key -> log.debug("Rate limit key: {}", key));
    }

    /**
     * IP-based key resolver for public endpoints.
     * Rate limits based on client IP address.
     */
    @Bean("ipKeyResolver")
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(getClientIp(exchange));
    }

    /**
     * User-based key resolver for authenticated endpoints.
     * Rate limits based on authenticated user principal.
     */
    @Bean("userKeyResolver")
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
            .map(Principal::getName)
            .defaultIfEmpty("anonymous");
    }

    /**
     * API key resolver for service-to-service communication.
     * Uses X-API-Key header if present.
     */
    @Bean("apiKeyResolver")
    public KeyResolver apiKeyResolver() {
        return exchange -> {
            String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");
            if (apiKey != null && !apiKey.isEmpty()) {
                return Mono.just("api:" + apiKey);
            }
            return Mono.just(getClientIp(exchange));
        };
    }

    /**
     * Extract the client IP for rate limiting. Delegates to {@link ClientIpResolver}
     * so {@code X-Forwarded-For} is only trusted from configured proxies and can
     * never be spoofed by a direct caller to escape the limit.
     */
    private String getClientIp(org.springframework.web.server.ServerWebExchange exchange) {
        return clientIpResolver.resolve(exchange);
    }
}
