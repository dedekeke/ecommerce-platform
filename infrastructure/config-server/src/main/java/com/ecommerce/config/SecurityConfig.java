package com.ecommerce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security Configuration for Config Server
 * Protects configuration endpoints with HTTP Basic Authentication
 * Using Spring Security 6.1+ lambda-based configuration
 * Using Argon2 password encoder for enhanced security
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        var user = User.builder()
            .username("configuser")
            .password(passwordEncoder().encode("configpass"))
            .roles("USER")
            .build();

        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Argon2 configuration: saltLength=16, hashLength=32, parallelism=1, memory=65536 KB, iterations=3
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
