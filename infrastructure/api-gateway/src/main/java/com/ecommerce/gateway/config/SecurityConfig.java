package com.ecommerce.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.List;

/**
 * Security configuration for API Gateway with Auth0 integration
 *
 * Features:
 * - JWT validation with issuer and audience checks
 * - CORS configuration for SPA frontends
 * - Public endpoints for health checks and product browsing
 * - Protected endpoints requiring authentication
 * - Toggle security with security.enabled property for local development
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${security.enabled:true}")
    private boolean securityEnabled;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String issuer;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private String jwkSetUri;

    @Value("${auth0.audience:}")
    private String audience;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        // Disable CORS in security - handled by Spring Cloud Gateway globalcors config
        http.cors(ServerHttpSecurity.CorsSpec::disable);

        if (!securityEnabled) {
            log.info("Security is DISABLED - all endpoints are public");
            http
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .csrf(ServerHttpSecurity.CsrfSpec::disable);
        } else {
            log.info("Security is ENABLED - JWT authentication required");
            http
                .authorizeExchange(exchanges -> exchanges
                    // Public endpoints
                    .pathMatchers("/actuator/**").permitAll()
                    // Swagger UI assets are public; the per-service api-docs proxied via
                    // /aggregate/<svc>/v3/api-docs require authentication in production so that
                    // internal API schemas are not exposed without a valid JWT.
                    .pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/webjars/**").permitAll()
                    .pathMatchers("/v3/api-docs", "/v3/api-docs/swagger-config").permitAll()
                    .pathMatchers("/aggregate/*/v3/api-docs/**").authenticated()
                    // Public catalog browsing — kept identical across the unversioned
                    // and /api/v1 routes. The gateway authorizes the ORIGINAL request
                    // path (the v1 RewritePath filter runs later during routing), so
                    // each versioned prefix must be listed explicitly or it falls
                    // through to anyExchange().authenticated() (Lore 2b8c4227).
                    .pathMatchers(HttpMethod.GET,
                        "/api/products", "/api/products/**",
                        "/api/v1/products", "/api/v1/products/**").permitAll()
                    .pathMatchers(HttpMethod.GET,
                        "/api/categories", "/api/categories/**",
                        "/api/v1/categories", "/api/v1/categories/**").permitAll()
                    .pathMatchers(HttpMethod.GET,
                        "/api/search/**", "/api/v1/search/**").permitAll()
                    .pathMatchers(HttpMethod.GET,
                        "/api/promotions/public/**", "/api/v1/promotions/public/**").permitAll()

                    // GraphQL BFF endpoint — per-query auth is enforced inside the
                    // resolvers via @PreAuthorize. The HTTP layer must permit the
                    // POST so GraphQL field-level errors carry through to clients
                    // rather than being short-circuited by the filter chain.
                    .pathMatchers("/graphql", "/graphiql", "/graphiql/**").permitAll()

                    // Admin endpoints require admin role
                    .pathMatchers("/api/admin/**").hasAuthority("SCOPE_admin")
                    // Product writes require admin on BOTH versions. The v1 route
                    // previously fell through to anyExchange().authenticated(), so any
                    // authenticated caller (not just admins) could mutate the catalog.
                    .pathMatchers(HttpMethod.POST, "/api/products/**", "/api/v1/products/**").hasAuthority("SCOPE_admin")
                    .pathMatchers(HttpMethod.PUT, "/api/products/**", "/api/v1/products/**").hasAuthority("SCOPE_admin")
                    .pathMatchers(HttpMethod.DELETE, "/api/products/**", "/api/v1/products/**").hasAuthority("SCOPE_admin")

                    // All other endpoints require authentication
                    .anyExchange().authenticated()
                )
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtDecoder(jwtDecoder()))
                );
        }

        return http.build();
    }

    /**
     * JWT Decoder with custom validators for Auth0. Only created when security
     * is enabled.
     *
     * <p>The decoder is built from the JWK Set URI rather than via
     * {@code withIssuerLocation(issuer)}. The latter performs a blocking OIDC
     * discovery HTTP call during bean construction (boot), so a transient Auth0
     * outage at startup crashes the whole gateway. {@code withJwkSetUri(...)}
     * defers the JWK fetch until the first token is decoded, so the gateway
     * boots independently of issuer reachability (Lore bug 1b8953dc).
     *
     * <p>If {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri} is not
     * explicitly configured we derive the standard JWKS endpoint from the
     * issuer ({@code <issuer>/.well-known/jwks.json}) so existing configs that
     * only set {@code issuer-uri} keep working without a boot-time call.
     *
     * <p>JWT validation is NOT weakened: the issuer claim is still enforced via
     * {@link JwtValidators#createDefaultWithIssuer(String)} (which also applies
     * the default timestamp checks) and the audience via {@link AudienceValidator},
     * both at request time.
     */
    @Bean
    @ConditionalOnProperty(name = "security.enabled", havingValue = "true", matchIfMissing = true)
    public ReactiveJwtDecoder jwtDecoder() {
        if (issuer == null || issuer.isEmpty()) {
            throw new IllegalStateException("JWT issuer URI must be configured when security is enabled");
        }

        NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder
            .withJwkSetUri(resolveJwkSetUri())
            .build();

        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(audience);
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> withIssuer = new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator);

        jwtDecoder.setJwtValidator(withIssuer);

        return jwtDecoder;
    }

    private String resolveJwkSetUri() {
        if (jwkSetUri != null && !jwkSetUri.isBlank()) {
            return jwkSetUri;
        }
        String base = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
        return base + "/.well-known/jwks.json";
    }

    /**
     * Custom audience validator for Auth0 JWT tokens
     */
    static class AudienceValidator implements OAuth2TokenValidator<Jwt> {
        private final String audience;

        AudienceValidator(String audience) {
            this.audience = audience;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            List<String> audiences = jwt.getAudience();
            if (audiences.contains(this.audience)) {
                return OAuth2TokenValidatorResult.success();
            }

            log.error("JWT token does not contain required audience: {}", audience);
            return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "The required audience is missing", null)
            );
        }
    }
}
