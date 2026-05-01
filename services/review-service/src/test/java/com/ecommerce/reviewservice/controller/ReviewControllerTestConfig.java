package com.ecommerce.reviewservice.controller;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Permissive security chain for {@link ReviewControllerTest}.
 *
 * <p>We keep the filter chain in the request lifecycle so
 * {@code .with(jwt())} from spring-security-test actually populates the
 * security context — but we permit every request to focus the assertions on
 * the controller contract rather than auth wiring (covered separately).
 */
@TestConfiguration
public class ReviewControllerTestConfig {

    @Bean
    @Primary
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authz -> authz.anyRequest().permitAll());
        return http.build();
    }
}
