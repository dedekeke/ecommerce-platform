package com.ecommerce.common.security.config;

import com.ecommerce.common.security.validator.AudienceValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Base security configuration for all microservices.
 * Provides common JWT decoder, audience validation, and security settings.
 *
 * Services should extend this configuration and customize as needed.
 *
 * This configuration is enabled by default. To disable for local development,
 * set security.enabled=false in application.yml
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "security.enabled", havingValue = "true", matchIfMissing = true)
public class BaseSecurityConfig {

    @Value("${auth0.domain}")
    private String auth0Domain;

    @Value("${auth0.audience}")
    private String auth0Audience;

    /**
     * Configures JWT decoder with Auth0 issuer and audience validation.
     *
     * @return configured JwtDecoder
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        String issuerUri = String.format("https://%s/", auth0Domain);
        NimbusJwtDecoder jwtDecoder = JwtDecoders.fromIssuerLocation(issuerUri);

        // Configure validators: issuer + audience
        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(auth0Audience);
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                audienceValidator
        );

        jwtDecoder.setJwtValidator(withAudience);
        return jwtDecoder;
    }

    /**
     * Configures JWT authentication converter to extract authorities from token.
     * Extracts both scopes and permissions from the JWT.
     *
     * @return configured JwtAuthenticationConverter
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

        // Extract authorities from "scope" claim (space-delimited)
        grantedAuthoritiesConverter.setAuthoritiesClaimName("scope");
        grantedAuthoritiesConverter.setAuthorityPrefix("SCOPE_");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);

        return jwtAuthenticationConverter;
    }

    /**
     * Default security filter chain configuration.
     * Services can override this method to customize security rules.
     *
     * @param http HttpSecurity to configure
     * @return configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for stateless API
            .csrf(AbstractHttpConfigurer::disable)

            // Configure stateless session management
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Configure authorization rules
            .authorizeHttpRequests(authz -> authz
                // Allow public access to health and info endpoints
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Allow public access to error endpoints
                .requestMatchers("/error").permitAll()
                // Require authentication for all other requests
                .anyRequest().authenticated()
            )

            // Configure OAuth2 resource server with JWT
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    /**
     * Extracts the Auth0 user ID (subject) from the JWT.
     *
     * @param jwt JWT token
     * @return user ID (sub claim)
     */
    public static String extractUserId(Jwt jwt) {
        return jwt.getSubject();
    }

    /**
     * Extracts a custom claim from the JWT.
     *
     * @param jwt JWT token
     * @param claimName name of the claim to extract
     * @return claim value as String, or null if not present
     */
    public static String extractClaim(Jwt jwt, String claimName) {
        return jwt.getClaimAsString(claimName);
    }

    /**
     * Checks if the JWT contains a specific scope.
     *
     * @param jwt JWT token
     * @param scope scope to check
     * @return true if scope is present, false otherwise
     */
    public static boolean hasScope(Jwt jwt, String scope) {
        String scopeClaim = jwt.getClaimAsString("scope");
        if (scopeClaim == null) {
            return false;
        }
        return scopeClaim.contains(scope);
    }

    /**
     * Checks if the JWT contains a specific permission.
     * Permissions are typically in the "permissions" claim as an array.
     *
     * @param jwt JWT token
     * @param permission permission to check
     * @return true if permission is present, false otherwise
     */
    public static boolean hasPermission(Jwt jwt, String permission) {
        var permissions = jwt.getClaimAsStringList("permissions");
        return permissions != null && permissions.contains(permission);
    }
}
