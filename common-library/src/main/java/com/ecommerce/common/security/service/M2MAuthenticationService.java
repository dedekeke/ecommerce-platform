package com.ecommerce.common.security.service;

import com.ecommerce.common.security.config.Auth0ClientCredentialsConfig;
import com.ecommerce.common.security.model.M2MToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

/**
 * Service for obtaining M2M access tokens from Auth0 using Client Credentials flow.
 * Used for service-to-service authentication. Only active when Auth0 M2M is
 * configured ({@code auth0.m2m.client-id}); otherwise it (and the cached variant)
 * back off so services/slices without M2M don't require its collaborators.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "auth0.m2m", name = "client-id")
@RequiredArgsConstructor
public class M2MAuthenticationService {

    private final Auth0ClientCredentialsConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Obtains an M2M access token from Auth0.
     *
     * @return M2MToken with access token and expiration info
     * @throws RuntimeException if token request fails
     */
    public M2MToken getAccessToken() {
        try {
            log.debug("Requesting M2M access token from Auth0");

            // Prepare request body
            MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("client_id", config.getClientId());
            requestBody.add("client_secret", config.getClientSecret());
            requestBody.add("audience", config.getAudience());
            requestBody.add("grant_type", "client_credentials");

            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);

            // Make request to Auth0 token endpoint
            ResponseEntity<String> response = restTemplate.exchange(
                    config.getTokenEndpoint(),
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                M2MToken token = objectMapper.readValue(response.getBody(), M2MToken.class);
                token.setObtainedAt(Instant.now());

                log.info("Successfully obtained M2M access token, expires in {} seconds", token.getExpiresIn());
                return token;
            } else {
                throw new RuntimeException("Failed to obtain M2M token: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error obtaining M2M access token", e);
            throw new RuntimeException("Failed to obtain M2M access token", e);
        }
    }

    /**
     * Obtains an M2M access token with specific scopes.
     *
     * @param scopes space-delimited scopes to request
     * @return M2MToken with access token and expiration info
     */
    public M2MToken getAccessTokenWithScopes(String scopes) {
        try {
            log.debug("Requesting M2M access token with scopes: {}", scopes);

            MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("client_id", config.getClientId());
            requestBody.add("client_secret", config.getClientSecret());
            requestBody.add("audience", config.getAudience());
            requestBody.add("grant_type", "client_credentials");
            requestBody.add("scope", scopes);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    config.getTokenEndpoint(),
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                M2MToken token = objectMapper.readValue(response.getBody(), M2MToken.class);
                token.setObtainedAt(Instant.now());

                log.info("Successfully obtained M2M access token with scopes, expires in {} seconds",
                        token.getExpiresIn());
                return token;
            } else {
                throw new RuntimeException("Failed to obtain M2M token: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error obtaining M2M access token with scopes", e);
            throw new RuntimeException("Failed to obtain M2M access token with scopes", e);
        }
    }

    /**
     * Creates an authorization header value from the token.
     *
     * @param token M2M token
     * @return "Bearer {accessToken}"
     */
    public String createAuthorizationHeader(M2MToken token) {
        return token.toAuthorizationHeader();
    }
}
