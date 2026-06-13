package com.ecommerce.common.security.service;

import com.ecommerce.common.security.model.M2MToken;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Cached version of M2M authentication service.
 * Stores tokens in Redis to avoid unnecessary calls to Auth0.
 */
@Slf4j
@RequiredArgsConstructor
public class CachedM2MAuthenticationService {

    private static final String TOKEN_CACHE_KEY = "m2m:token:default";
    private static final String TOKEN_SCOPE_CACHE_PREFIX = "m2m:token:scope:";

    private final M2MAuthenticationService m2mAuthenticationService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Gets an M2M access token, using cached version if available and not expired.
     *
     * @return M2MToken with access token
     */
    public M2MToken getAccessToken() {
        try {
            // Try to get from cache
            String cachedToken = redisTemplate.opsForValue().get(TOKEN_CACHE_KEY);

            if (cachedToken != null) {
                M2MToken token = objectMapper.readValue(cachedToken, M2MToken.class);

                if (!token.isExpired()) {
                    log.debug("Using cached M2M token, {} seconds remaining",
                            token.getRemainingSeconds());
                    return token;
                } else {
                    log.debug("Cached M2M token is expired, fetching new one");
                    redisTemplate.delete(TOKEN_CACHE_KEY);
                }
            }

            // Get new token
            M2MToken newToken = m2mAuthenticationService.getAccessToken();

            // Cache it with TTL slightly less than token expiration
            cacheToken(TOKEN_CACHE_KEY, newToken);

            return newToken;

        } catch (JsonProcessingException e) {
            log.error("Error processing cached token, fetching new one", e);
            return m2mAuthenticationService.getAccessToken();
        }
    }

    /**
     * Gets an M2M access token with specific scopes, using cached version if available.
     *
     * @param scopes space-delimited scopes
     * @return M2MToken with access token
     */
    public M2MToken getAccessTokenWithScopes(String scopes) {
        String cacheKey = TOKEN_SCOPE_CACHE_PREFIX + scopes.replace(" ", "_");

        try {
            // Try to get from cache
            String cachedToken = redisTemplate.opsForValue().get(cacheKey);

            if (cachedToken != null) {
                M2MToken token = objectMapper.readValue(cachedToken, M2MToken.class);

                if (!token.isExpired()) {
                    log.debug("Using cached M2M token with scopes, {} seconds remaining",
                            token.getRemainingSeconds());
                    return token;
                } else {
                    log.debug("Cached M2M token with scopes is expired, fetching new one");
                    redisTemplate.delete(cacheKey);
                }
            }

            // Get new token
            M2MToken newToken = m2mAuthenticationService.getAccessTokenWithScopes(scopes);

            // Cache it
            cacheToken(cacheKey, newToken);

            return newToken;

        } catch (JsonProcessingException e) {
            log.error("Error processing cached token, fetching new one", e);
            return m2mAuthenticationService.getAccessTokenWithScopes(scopes);
        }
    }

    /**
     * Caches a token in Redis with appropriate TTL.
     *
     * @param cacheKey Redis key
     * @param token token to cache
     */
    private void cacheToken(String cacheKey, M2MToken token) {
        try {
            String tokenJson = objectMapper.writeValueAsString(token);

            // Set TTL to token expiration minus 5 minutes buffer
            long ttlSeconds = Math.max(60, token.getExpiresIn() - 300);

            redisTemplate.opsForValue().set(cacheKey, tokenJson, ttlSeconds, TimeUnit.SECONDS);

            log.debug("Cached M2M token with TTL of {} seconds", ttlSeconds);

        } catch (JsonProcessingException e) {
            log.error("Error caching token", e);
        }
    }

    /**
     * Invalidates (clears) the cached token.
     * Useful when a token needs to be refreshed immediately.
     */
    public void invalidateCache() {
        log.info("Invalidating M2M token cache");
        redisTemplate.delete(TOKEN_CACHE_KEY);

        // Also clear any scope-specific tokens
        var keys = redisTemplate.keys(TOKEN_SCOPE_CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * Invalidates cached token for specific scopes.
     *
     * @param scopes scopes to invalidate
     */
    public void invalidateCache(String scopes) {
        String cacheKey = TOKEN_SCOPE_CACHE_PREFIX + scopes.replace(" ", "_");
        log.info("Invalidating M2M token cache for scopes: {}", scopes);
        redisTemplate.delete(cacheKey);
    }

    /**
     * Creates an authorization header value from the cached token.
     *
     * @return "Bearer {accessToken}"
     */
    public String getAuthorizationHeader() {
        M2MToken token = getAccessToken();
        return token.toAuthorizationHeader();
    }

    /**
     * Creates an authorization header value with specific scopes.
     *
     * @param scopes scopes to request
     * @return "Bearer {accessToken}"
     */
    public String getAuthorizationHeaderWithScopes(String scopes) {
        M2MToken token = getAccessTokenWithScopes(scopes);
        return token.toAuthorizationHeader();
    }
}
