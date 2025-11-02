package com.ecommerce.common.security.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents an M2M access token from Auth0.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class M2MToken {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private Long expiresIn;

    private String scope;

    /**
     * The timestamp when this token was obtained
     */
    private Instant obtainedAt;

    /**
     * Checks if the token is expired.
     * Adds a 5-minute buffer to ensure we refresh before actual expiration.
     *
     * @return true if token is expired or about to expire
     */
    public boolean isExpired() {
        if (obtainedAt == null || expiresIn == null) {
            return true;
        }

        // Add 5-minute buffer (300 seconds)
        long bufferSeconds = 300;
        Instant expirationTime = obtainedAt.plusSeconds(expiresIn - bufferSeconds);

        return Instant.now().isAfter(expirationTime);
    }

    /**
     * Gets the remaining time until expiration in seconds.
     *
     * @return remaining seconds, or 0 if expired
     */
    public long getRemainingSeconds() {
        if (obtainedAt == null || expiresIn == null) {
            return 0;
        }

        Instant expirationTime = obtainedAt.plusSeconds(expiresIn);
        long remaining = expirationTime.getEpochSecond() - Instant.now().getEpochSecond();

        return Math.max(0, remaining);
    }

    /**
     * Gets the authorization header value for HTTP requests.
     *
     * @return "Bearer {accessToken}"
     */
    public String toAuthorizationHeader() {
        return String.format("%s %s", tokenType != null ? tokenType : "Bearer", accessToken);
    }
}
