package com.ecommerce.mediaservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${security.enabled:true}")
    private boolean securityEnabled;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String issuerUri;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        if (!securityEnabled) {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            http
                    .csrf(csrf -> csrf.disable())
                    .sessionManagement(session ->
                            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(
                                    "/actuator/health",
                                    "/actuator/info",
                                    "/actuator/prometheus",
                                    "/v3/api-docs/**",
                                    "/swagger-ui/**",
                                    "/swagger-ui.html"
                            ).permitAll()
                            // Public product-imagery read path (ADR, PR#153 review):
                            // media is owner-private until attached to a product;
                            // product imagery is a public asset. Metadata (/{id}),
                            // /download and /user/{userId} stay owner-gated.
                            .requestMatchers(HttpMethod.GET, "/api/media/*/content").permitAll()
                            .anyRequest().authenticated()
                    )
                    .oauth2ResourceServer(oauth2 -> oauth2
                            .jwt(jwt -> jwt.jwtAuthenticationConverter(new CustomJwtAuthenticationConverter()))
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
