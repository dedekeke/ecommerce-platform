package com.ecommerce.common.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Auth0 M2M (Machine-to-Machine) authentication.
 * Used for service-to-service communication via Client Credentials flow.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "auth0.m2m")
public class Auth0ClientCredentialsConfig {

    /**
     * Auth0 domain (e.g., "your-tenant.auth0.com")
     */
    private String domain;

    /**
     * Client ID for M2M application
     */
    private String clientId;

    /**
     * Client Secret for M2M application
     */
    private String clientSecret;

    /**
     * Audience for the M2M token (API identifier)
     */
    private String audience;

    /**
     * Token endpoint URL (derived from domain if not specified)
     */
    private String tokenEndpoint;

    /**
     * Gets the token endpoint URL.
     * Constructs it from domain if not explicitly set.
     *
     * @return token endpoint URL
     */
    public String getTokenEndpoint() {
        if (tokenEndpoint != null && !tokenEndpoint.isEmpty()) {
            return tokenEndpoint;
        }
        return String.format("https://%s/oauth/token", domain);
    }
}
