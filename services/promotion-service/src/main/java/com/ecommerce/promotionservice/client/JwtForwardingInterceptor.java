package com.ecommerce.promotionservice.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.io.IOException;
import java.util.Optional;

/**
 * Forwards the current request's JWT as a {@code Bearer} token on outgoing
 * RestClient calls.
 *
 * <p>Without this, the promotion-service → user-service lookup goes out
 * unauthenticated. In the docker/prod profile user-service rejects it with 401,
 * the client swallows the error and returns {@link java.util.Optional#empty()},
 * so every targeted promotion silently degrades to a broadcast. Mirrors
 * cart-service's Feign {@code authForwardingInterceptor}.
 *
 * <p>When there is no authenticated JWT in the {@link SecurityContextHolder}
 * (e.g. the scheduled currency job runs on a background thread with no security
 * context) the header is simply omitted — this interceptor never fabricates a
 * token.
 */
public class JwtForwardingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        currentTokenValue().ifPresent(token ->
                request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        return execution.execute(request, body);
    }

    private Optional<String> currentTokenValue() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(jwt.getTokenValue());
        }
        return Optional.empty();
    }
}
