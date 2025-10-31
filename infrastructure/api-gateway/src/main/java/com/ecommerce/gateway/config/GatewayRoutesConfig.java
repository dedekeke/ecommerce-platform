package com.ecommerce.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * Gateway Routes Configuration
 *
 * Defines routes to all microservices with:
 * - Service discovery via Eureka (lb://<service-name>)
 * - Token relay filter for JWT propagation
 * - Path rewriting
 * - Circuit breaker patterns (to be added)
 */
@Slf4j
@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            // User Service Routes
            .route("user-service", r -> r
                .path("/api/users/**")
                .filters(f -> f
                    .stripPrefix(1)  // Remove /api prefix
                    .tokenRelay()    // Propagate JWT token
                )
                .uri("lb://user-service")
            )

            // Product Service Routes
            .route("product-service", r -> r
                .path("/api/products/**")
                .filters(f -> f
                    .stripPrefix(1)
                    .tokenRelay()
                )
                .uri("lb://product-service")
            )

            // Cart Service Routes
            .route("cart-service", r -> r
                .path("/api/cart/**")
                .filters(f -> f
                    .stripPrefix(1)
                    .tokenRelay()
                )
                .uri("lb://cart-service")
            )

            // Order Service Routes
            .route("order-service", r -> r
                .path("/api/orders/**")
                .filters(f -> f
                    .stripPrefix(1)
                    .tokenRelay()
                )
                .uri("lb://order-service")
            )

            // Payment Service Routes
            .route("payment-service", r -> r
                .path("/api/payments/**")
                .filters(f -> f
                    .stripPrefix(1)
                    .tokenRelay()
                )
                .uri("lb://payment-service")
            )

            // Inventory Service Routes
            .route("inventory-service", r -> r
                .path("/api/inventory/**")
                .filters(f -> f
                    .stripPrefix(1)
                    .tokenRelay()
                )
                .uri("lb://inventory-service")
            )

            // Notification Service Routes
            .route("notification-service", r -> r
                .path("/api/notifications/**")
                .filters(f -> f
                    .stripPrefix(1)
                    .tokenRelay()
                )
                .uri("lb://notification-service")
            )

            // Search Service Routes
            .route("search-service", r -> r
                .path("/api/search/**")
                .filters(f -> f
                    .stripPrefix(1)
                    .tokenRelay()
                )
                .uri("lb://search-service")
            )

            // Media Service Routes
            .route("media-service", r -> r
                .path("/api/media/**")
                .filters(f -> f
                    .stripPrefix(1)
                    .tokenRelay()
                )
                .uri("lb://media-service")
            )

            // Promotion Service Routes
            .route("promotion-service", r -> r
                .path("/api/promotions/**")
                .filters(f -> f
                    .stripPrefix(1)
                    .tokenRelay()
                )
                .uri("lb://promotion-service")
            )

            // Admin routes - separate for better security control
            .route("admin-products", r -> r
                .path("/api/admin/products/**")
                .and()
                .method(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
                .filters(f -> f
                    .stripPrefix(2)  // Remove /api/admin
                    .tokenRelay()
                )
                .uri("lb://product-service")
            )

            .route("admin-inventory", r -> r
                .path("/api/admin/inventory/**")
                .filters(f -> f
                    .stripPrefix(2)
                    .tokenRelay()
                )
                .uri("lb://inventory-service")
            )

            .route("admin-orders", r -> r
                .path("/api/admin/orders/**")
                .filters(f -> f
                    .stripPrefix(2)
                    .tokenRelay()
                )
                .uri("lb://order-service")
            )

            .route("admin-users", r -> r
                .path("/api/admin/users/**")
                .filters(f -> f
                    .stripPrefix(2)
                    .tokenRelay()
                )
                .uri("lb://user-service")
            )

            .build();
    }
}
