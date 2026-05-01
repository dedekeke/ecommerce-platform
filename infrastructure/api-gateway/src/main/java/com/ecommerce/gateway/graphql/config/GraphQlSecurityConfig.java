package com.ecommerce.gateway.graphql.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;

/**
 * Enables {@code @PreAuthorize} on the BFF GraphQL controllers.
 *
 * <p>Public queries — {@code product}, {@code products}, {@code recommendations}
 * — carry no annotation and are therefore reachable without a JWT.
 *
 * <p>Authenticated queries — {@code cart}, {@code order}, {@code myOrders} —
 * are guarded by {@code @PreAuthorize("isAuthenticated()")}. The reactive JWT
 * filter chain populates the security context; an unauthenticated principal
 * causes the resolver to surface a {@code GraphQL ErrorType.FORBIDDEN}.
 */
@Configuration
@EnableReactiveMethodSecurity
public class GraphQlSecurityConfig {
}
