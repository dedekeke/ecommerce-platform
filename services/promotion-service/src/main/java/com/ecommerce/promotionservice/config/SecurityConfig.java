package com.ecommerce.promotionservice.config;

import com.ecommerce.promotionservice.security.InternalServiceTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${security.enabled:true}")
    private boolean securityEnabled;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String issuerUri;

    /**
     * Shared secret proving a caller is a trusted platform service. Sourced from
     * {@code INTERNAL_SERVICE_TOKEN}; must match the value order-service sends.
     */
    @Value("${security.internal.service-token:}")
    private String internalServiceToken;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        if (!securityEnabled) {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            requireInternalServiceToken();
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/actuator/**").permitAll()
                            .requestMatchers("/swagger-ui/**", "/api-docs/**", "/swagger-ui.html").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/promotions", "/api/promotions/**").permitAll()
                            // Stays public: guests validate a promo code from the
                            // cart UI before any login. Enumeration is blunted by
                            // the gateway's per-IP rate limit on this path.
                            .requestMatchers(HttpMethod.POST, "/api/promotions/validate").permitAll()
                            // Mutates state (increments usage counters), so it is
                            // restricted to service callers. Browsers must never
                            // reach it; only order-service's saga applies a code.
                            .requestMatchers(HttpMethod.POST, "/api/promotions/apply")
                            .hasAnyAuthority(InternalServiceTokenFilter.INTERNAL_SERVICE_AUTHORITY,
                                    "SCOPE_internal:service")
                            .anyRequest().authenticated()
                    )
                    .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder())))
                    .addFilterAfter(new InternalServiceTokenFilter(internalServiceToken),
                            BearerTokenAuthenticationFilter.class);
        }

        return http.build();
    }

    /**
     * Fail fast rather than boot into a state where every service call to
     * {@code /apply} is silently rejected and promotion usage counters stop
     * incrementing. Mirrors the issuer-uri guard below.
     */
    private void requireInternalServiceToken() {
        if (internalServiceToken == null || internalServiceToken.isBlank()) {
            throw new IllegalStateException(
                    "INTERNAL_SERVICE_TOKEN must be configured when security is enabled "
                            + "(guards POST /api/promotions/apply)");
        }
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
