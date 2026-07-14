package com.ecommerce.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * Programmatic gateway routes (complements the declarative routes in
 * {@code application.yml}).
 *
 * <p>The unversioned routes here are kept for backwards compatibility and
 * stamped with {@code Deprecation: true} + a {@code Sunset:} header per
 * {@code docs/API_VERSIONING.md}. Versioned (`/api/v1/...`) routes are
 * defined alongside and strip the {@code /v1} prefix so backend services do
 * not need to know about the gateway's versioning scheme.
 */
@Slf4j
@Configuration
public class GatewayRoutesConfig {

    /** Default sunset timestamp for unversioned routes. Override per-env. */
    @Value("${api.deprecation.sunset:Fri, 30 Apr 2027 23:59:59 GMT}")
    private String sunset;

    /**
     * Per-route response-timeout (ms) for payment routes, raised above the 5s
     * global default because 3rd-party PSPs can be slower than internal
     * services. Kept in sync with the declarative payment routes in
     * application.yml so the override holds whichever route locator serves.
     */
    @Value("${GATEWAY_PAYMENT_RESPONSE_TIMEOUT:10000}")
    private int paymentResponseTimeoutMs;

    @Bean
    @ConditionalOnProperty(name = "gateway.programmatic-routes.enabled", havingValue = "true", matchIfMissing = true)
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            // ---------------- User Service ----------------
            .route("user-service-prog", r -> r
                .path("/api/users/**")
                .filters(f -> f
                    .tokenRelay()
                    .addResponseHeader("Deprecation", "true")
                    .addResponseHeader("Sunset", sunset)
                )
                .uri("lb://user-service")
            )
            .route("user-service-v1-prog", r -> r
                .path("/api/v1/users/**")
                .filters(f -> f
                    .rewritePath("/api/v1/(?<segment>.*)", "/api/${segment}")
                    .addRequestHeader("X-API-Version", "v1")
                    .tokenRelay()
                )
                .uri("lb://user-service")
            )

            // ---------------- Wishlist (under user-service) ----------------
            .route("wishlist-service-prog", r -> r
                .path("/api/wishlist/**")
                .filters(f -> f
                    .tokenRelay()
                    .addResponseHeader("Deprecation", "true")
                    .addResponseHeader("Sunset", sunset)
                )
                .uri("lb://user-service")
            )
            .route("wishlist-service-v1-prog", r -> r
                .path("/api/v1/wishlist/**")
                .filters(f -> f
                    .rewritePath("/api/v1/(?<segment>.*)", "/api/${segment}")
                    .addRequestHeader("X-API-Version", "v1")
                    .tokenRelay()
                )
                .uri("lb://user-service")
            )

            // ---------------- Product Service ----------------
            .route("product-service-prog", r -> r
                .path("/api/products/**")
                .filters(f -> f
                    .tokenRelay()
                    .addResponseHeader("Deprecation", "true")
                    .addResponseHeader("Sunset", sunset)
                )
                .uri("lb://product-service")
            )
            .route("product-service-v1-prog", r -> r
                .path("/api/v1/products/**", "/api/v1/categories/**")
                .filters(f -> f
                    .rewritePath("/api/v1/(?<segment>.*)", "/api/${segment}")
                    .addRequestHeader("X-API-Version", "v1")
                    .tokenRelay()
                )
                .uri("lb://product-service")
            )

            // ---------------- Cart Service ----------------
            .route("cart-service-prog", r -> r
                .path("/api/cart/**", "/api/carts/**")
                .filters(f -> f
                    .tokenRelay()
                    .addResponseHeader("Deprecation", "true")
                    .addResponseHeader("Sunset", sunset)
                )
                .uri("lb://cart-service")
            )
            .route("cart-service-v1-prog", r -> r
                .path("/api/v1/cart/**", "/api/v1/carts/**")
                .filters(f -> f
                    .rewritePath("/api/v1/(?<segment>.*)", "/api/${segment}")
                    .addRequestHeader("X-API-Version", "v1")
                    .tokenRelay()
                )
                .uri("lb://cart-service")
            )

            // ---------------- Order Service ----------------
            .route("order-service-prog", r -> r
                .path("/api/orders/**")
                .filters(f -> f
                    .tokenRelay()
                    .addResponseHeader("Deprecation", "true")
                    .addResponseHeader("Sunset", sunset)
                )
                .uri("lb://order-service")
            )
            .route("order-service-v1-prog", r -> r
                .path("/api/v1/orders/**")
                .filters(f -> f
                    .rewritePath("/api/v1/(?<segment>.*)", "/api/${segment}")
                    .addRequestHeader("X-API-Version", "v1")
                    .tokenRelay()
                )
                .uri("lb://order-service")
            )

            // ---------------- Payment Service ----------------
            // Raised response-timeout: PSPs can be slower than internal services.
            .route("payment-service-prog", r -> r
                .path("/api/payments/**")
                .filters(f -> f
                    .tokenRelay()
                    .addResponseHeader("Deprecation", "true")
                    .addResponseHeader("Sunset", sunset)
                )
                .metadata("response-timeout", paymentResponseTimeoutMs)
                .uri("lb://payment-service")
            )
            .route("payment-service-v1-prog", r -> r
                .path("/api/v1/payments/**")
                .filters(f -> f
                    .rewritePath("/api/v1/(?<segment>.*)", "/api/${segment}")
                    .addRequestHeader("X-API-Version", "v1")
                    .tokenRelay()
                )
                .metadata("response-timeout", paymentResponseTimeoutMs)
                .uri("lb://payment-service")
            )

            // ---------------- Inventory Service ----------------
            .route("inventory-service-prog", r -> r
                .path("/api/inventory/**")
                .filters(f -> f
                    .tokenRelay()
                    .addResponseHeader("Deprecation", "true")
                    .addResponseHeader("Sunset", sunset)
                )
                .uri("lb://inventory-service")
            )
            .route("inventory-service-v1-prog", r -> r
                .path("/api/v1/inventory/**")
                .filters(f -> f
                    .rewritePath("/api/v1/(?<segment>.*)", "/api/${segment}")
                    .addRequestHeader("X-API-Version", "v1")
                    .tokenRelay()
                )
                .uri("lb://inventory-service")
            )

            // ---------------- Notification Service ----------------
            .route("notification-service-prog", r -> r
                .path("/api/notifications/**")
                .filters(f -> f
                    .tokenRelay()
                    .addResponseHeader("Deprecation", "true")
                    .addResponseHeader("Sunset", sunset)
                )
                .uri("lb://notification-service")
            )
            .route("notification-service-v1-prog", r -> r
                .path("/api/v1/notifications/**")
                .filters(f -> f
                    .rewritePath("/api/v1/(?<segment>.*)", "/api/${segment}")
                    .addRequestHeader("X-API-Version", "v1")
                    .tokenRelay()
                )
                .uri("lb://notification-service")
            )

            // ---------------- Search Service ----------------
            .route("search-service-prog", r -> r
                .path("/api/search/**")
                .filters(f -> f
                    .tokenRelay()
                    .addResponseHeader("Deprecation", "true")
                    .addResponseHeader("Sunset", sunset)
                )
                .uri("lb://search-service")
            )
            .route("search-service-v1-prog", r -> r
                .path("/api/v1/search/**")
                .filters(f -> f
                    .rewritePath("/api/v1/(?<segment>.*)", "/api/${segment}")
                    .addRequestHeader("X-API-Version", "v1")
                    .tokenRelay()
                )
                .uri("lb://search-service")
            )

            // ---------------- Media Service ----------------
            .route("media-service-prog", r -> r
                .path("/api/media/**")
                .filters(f -> f
                    .tokenRelay()
                    .addResponseHeader("Deprecation", "true")
                    .addResponseHeader("Sunset", sunset)
                )
                .uri("lb://media-service")
            )
            .route("media-service-v1-prog", r -> r
                .path("/api/v1/media/**")
                .filters(f -> f
                    .rewritePath("/api/v1/(?<segment>.*)", "/api/${segment}")
                    .addRequestHeader("X-API-Version", "v1")
                    .tokenRelay()
                )
                .uri("lb://media-service")
            )

            // ---------------- Promotion Service ----------------
            .route("promotion-service-prog", r -> r
                .path("/api/promotions/**")
                .filters(f -> f
                    .tokenRelay()
                    .addResponseHeader("Deprecation", "true")
                    .addResponseHeader("Sunset", sunset)
                )
                .uri("lb://promotion-service")
            )
            .route("promotion-service-v1-prog", r -> r
                .path("/api/v1/promotions/**")
                .filters(f -> f
                    .rewritePath("/api/v1/(?<segment>.*)", "/api/${segment}")
                    .addRequestHeader("X-API-Version", "v1")
                    .tokenRelay()
                )
                .uri("lb://promotion-service")
            )

            // ---------------- Recommendation Service ----------------
            .route("recommendation-service-prog", r -> r
                .path("/api/recommendations/**")
                .filters(f -> f
                    .tokenRelay()
                    .addResponseHeader("Deprecation", "true")
                    .addResponseHeader("Sunset", sunset)
                )
                .uri("lb://recommendation-service")
            )
            .route("recommendation-service-v1-prog", r -> r
                .path("/api/v1/recommendations/**")
                .filters(f -> f
                    .rewritePath("/api/v1/(?<segment>.*)", "/api/${segment}")
                    .addRequestHeader("X-API-Version", "v1")
                    .tokenRelay()
                )
                .uri("lb://recommendation-service")
            )

            // ---------------- Review Service (NEW: §3.4) ----------------
            .route("review-service-prog", r -> r
                .path("/api/reviews/**")
                .filters(f -> f
                    .tokenRelay()
                    .addResponseHeader("Deprecation", "true")
                    .addResponseHeader("Sunset", sunset)
                )
                .uri("lb://review-service")
            )
            .route("review-service-v1-prog", r -> r
                .path("/api/v1/reviews/**")
                .filters(f -> f
                    .rewritePath("/api/v1/(?<segment>.*)", "/api/${segment}")
                    .addRequestHeader("X-API-Version", "v1")
                    .tokenRelay()
                )
                .uri("lb://review-service")
            )

            // ---------------- Admin routes (kept as-is) ----------------
            .route("admin-products", r -> r
                .path("/api/admin/products/**")
                .and()
                .method(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
                .filters(f -> f
                    .rewritePath("/api/admin/(?<segment>.*)", "/api/${segment}")
                    .tokenRelay()
                )
                .uri("lb://product-service")
            )
            .route("admin-inventory", r -> r
                .path("/api/admin/inventory/**")
                .filters(f -> f
                    .rewritePath("/api/admin/(?<segment>.*)", "/api/${segment}")
                    .tokenRelay()
                )
                .uri("lb://inventory-service")
            )
            .route("admin-orders", r -> r
                .path("/api/admin/orders/**")
                .filters(f -> f
                    .rewritePath("/api/admin/(?<segment>.*)", "/api/${segment}")
                    .tokenRelay()
                )
                .uri("lb://order-service")
            )
            .route("admin-users", r -> r
                .path("/api/admin/users/**")
                .filters(f -> f
                    .rewritePath("/api/admin/(?<segment>.*)", "/api/${segment}")
                    .tokenRelay()
                )
                .uri("lb://user-service")
            )

            .build();
    }
}
