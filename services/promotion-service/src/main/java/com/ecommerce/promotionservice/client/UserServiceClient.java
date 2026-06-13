package com.ecommerce.promotionservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Optional;

/**
 * Resolves user contact details from user-service by Auth0 {@code sub}.
 *
 * <p>Used by promotion targeting to turn a targeted user id into an email
 * address. Failures (service down, user not found) return
 * {@link Optional#empty()} so callers can fall back to broadcast rather than
 * failing the announcement.
 */
@Slf4j
@Component
public class UserServiceClient {

    private final RestClient restClient;

    public UserServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${user.service.url:http://user-service:8085}") String userServiceUrl,
            @Value("${user.service.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${user.service.read-timeout-ms:3000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = restClientBuilder
                .baseUrl(userServiceUrl)
                .requestFactory(requestFactory)
                // Forward the caller's JWT so user-service authorises the lookup;
                // without this the call is anonymous and targeting silently
                // falls back to broadcast in secured environments.
                .requestInterceptor(new JwtForwardingInterceptor())
                .build();
    }

    public Optional<UserContact> findByAuth0Id(String auth0Id) {
        if (auth0Id == null || auth0Id.isBlank()) {
            return Optional.empty();
        }
        try {
            // Pass the raw sub as a URI variable — RestClient encodes it once
            // (e.g. the "|" in "auth0|abc" becomes %7C). Pre-encoding here would
            // double-encode it.
            UserContact contact = restClient.get()
                    .uri("/api/users/by-auth0/{sub}", auth0Id)
                    .retrieve()
                    .body(UserContact.class);
            return Optional.ofNullable(contact);
        } catch (RestClientException e) {
            log.warn("Failed to resolve user {} from user-service: {}", auth0Id, e.getMessage());
            return Optional.empty();
        }
    }
}
