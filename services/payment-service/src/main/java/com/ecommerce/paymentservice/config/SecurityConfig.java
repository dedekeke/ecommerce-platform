package com.ecommerce.paymentservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the Payment Service REST surface.
 *
 * <p>Mirrors the cart-service pattern: with {@code security.enabled=false} (local dev) every
 * request is permitted; otherwise the {@code /api/**} endpoints require a valid Auth0 JWT so the
 * PaymentIntent endpoint can derive the owning user from the token subject.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${security.enabled:true}")
    private boolean securityEnabled;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String issuerUri;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        if (!securityEnabled) {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize
                            .anyRequest().permitAll()
                    );
        } else {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                            // Stripe cannot present a JWT: the webhook authenticates via the
                            // Stripe-Signature HMAC (verified in DefaultStripeWebhookVerifier),
                            // NOT the resource server. Permit the path here and rely on signature
                            // verification for auth. Must precede the /api/** rule below.
                            .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
                            .requestMatchers("/api/**").authenticated()
                            .anyRequest().authenticated()
                    )
                    .oauth2ResourceServer(oauth2 -> oauth2
                            .jwt(jwt -> jwt.decoder(jwtDecoder()))
                    );
        }

        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "security.enabled", havingValue = "true", matchIfMissing = true)
    public JwtDecoder jwtDecoder() {
        if (issuerUri == null || issuerUri.isEmpty()) {
            throw new IllegalStateException("JWT issuer URI must be configured when security is enabled");
        }
        return NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
    }
}
