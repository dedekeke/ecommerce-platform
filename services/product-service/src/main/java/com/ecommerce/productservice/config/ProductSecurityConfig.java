package com.ecommerce.productservice.config;

import com.ecommerce.common.security.validator.AudienceValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
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
 * Security configuration for Product Service.
 * Allows public read access to products and categories while protecting write operations.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "security.enabled", havingValue = "true", matchIfMissing = true)
public class ProductSecurityConfig {

    @Value("${auth0.domain}")
    private String auth0Domain;

    @Value("${auth0.audience}")
    private String auth0Audience;

    @Bean
    public JwtDecoder jwtDecoder() {
        String issuerUri = String.format("https://%s/", auth0Domain);
        NimbusJwtDecoder jwtDecoder = JwtDecoders.fromIssuerLocation(issuerUri);

        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(auth0Audience);
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                audienceValidator
        );

        jwtDecoder.setJwtValidator(withAudience);
        return jwtDecoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("scope");
        grantedAuthoritiesConverter.setAuthorityPrefix("SCOPE_");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);

        return jwtAuthenticationConverter;
    }

    @Bean
    @Primary
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(AbstractHttpConfigurer::disable)  // CORS handled by API Gateway
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                // Public read access to products and categories.
                // The service always receives the post-rewrite, unversioned paths:
                // the gateway forwards /api/categories/** as-is and strips /v1 from
                // /api/v1/categories/** before routing (RewritePath). Matchers must
                // therefore key on /api/categories/** — the CategoryController mapping
                // — or they silently stop protecting real traffic.
                .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/categories", "/api/categories/**").permitAll()

                // Admin operations require authentication and admin scope.
                // PATCH is enumerated alongside POST/PUT/DELETE so partial mutations
                // (PATCH /api/products/{id}/stock, PATCH /api/categories/{id}/move)
                // cannot fall through to plain authenticated(). This mirrors the gateway
                // matchers from PR#108; the service layer is the real defense because
                // internal callers (cart/order via load-balanced WebClient) BYPASS the gateway.
                .requestMatchers(HttpMethod.POST, "/api/products/**").hasAuthority("SCOPE_admin")
                .requestMatchers(HttpMethod.PUT, "/api/products/**").hasAuthority("SCOPE_admin")
                .requestMatchers(HttpMethod.PATCH, "/api/products/**").hasAuthority("SCOPE_admin")
                .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasAuthority("SCOPE_admin")
                .requestMatchers(HttpMethod.POST, "/api/categories/**").hasAuthority("SCOPE_admin")
                .requestMatchers(HttpMethod.PUT, "/api/categories/**").hasAuthority("SCOPE_admin")
                .requestMatchers(HttpMethod.PATCH, "/api/categories/**").hasAuthority("SCOPE_admin")
                .requestMatchers(HttpMethod.DELETE, "/api/categories/**").hasAuthority("SCOPE_admin")

                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }
}
