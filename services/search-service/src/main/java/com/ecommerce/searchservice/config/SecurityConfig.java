package com.ecommerce.searchservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for search-service.
 *
 * <p>Previously search-service had no {@link SecurityFilterChain} or
 * {@code @EnableMethodSecurity}, so Boot's default security applied and any
 * {@code @PreAuthorize} was silently ignored — leaving the admin backfill
 * endpoint guarded only by a feature flag. This config:
 * <ul>
 *   <li>keeps the public product-search endpoints ({@code /api/search/**}) open,</li>
 *   <li>requires a valid JWT for the admin namespace ({@code /api/admin/**}),</li>
 *   <li>enables method security so {@code @PreAuthorize} on the backfill
 *       controller is actually enforced.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Value("${security.enabled:true}")
    private boolean securityEnabled;

    @Value("${auth0.domain:}")
    private String auth0Domain;

    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        if (!securityEnabled) {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
            return http.build();
        }

        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        // Public product search/browse
                        .requestMatchers(HttpMethod.GET, "/api/search/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/search/products").permitAll()
                        // Admin namespace requires authentication; method-level
                        // @PreAuthorize enforces the admin scope.
                        .requestMatchers("/api/admin/**").authenticated()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder())));

        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "security.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder() {
        if (auth0Domain == null || auth0Domain.isBlank()) {
            throw new IllegalStateException("auth0.domain must be configured when security is enabled");
        }
        return NimbusJwtDecoder.withIssuerLocation("https://" + auth0Domain + "/").build();
    }
}
