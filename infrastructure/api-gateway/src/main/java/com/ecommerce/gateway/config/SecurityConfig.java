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
                    .pathMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                    .pathMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/categories/**").permitAll()
                    .pathMatchers(HttpMethod.GET, "/api/search/**").permitAll()
                    .pathMatchers(HttpMethod.GET, "/api/promotions/public/**").permitAll()

                    // Admin endpoints require admin role
                    .pathMatchers("/api/admin/**").hasAuthority("SCOPE_admin")
                    .pathMatchers(HttpMethod.POST, "/api/products/**").hasAuthority("SCOPE_admin")
                    .pathMatchers(HttpMethod.PUT, "/api/products/**").hasAuthority("SCOPE_admin")
                    .pathMatchers(HttpMethod.DELETE, "/api/products/**").hasAuthority("SCOPE_admin")

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
     * JWT Decoder with custom validators for Auth0
     * Only created when security is enabled
     */
    @Bean
    @ConditionalOnProperty(name = "security.enabled", havingValue = "true", matchIfMissing = true)
    public ReactiveJwtDecoder jwtDecoder() {
        if (issuer == null || issuer.isEmpty()) {
            throw new IllegalStateException("JWT issuer URI must be configured when security is enabled");
        }

        NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder
            .withIssuerLocation(issuer)
            .build();

        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(audience);
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> withIssuer = new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator);

        jwtDecoder.setJwtValidator(withIssuer);

        return jwtDecoder;
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
