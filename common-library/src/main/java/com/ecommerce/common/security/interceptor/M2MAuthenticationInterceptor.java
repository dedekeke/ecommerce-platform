package com.ecommerce.common.security.interceptor;

import com.ecommerce.common.security.service.CachedM2MAuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Interceptor that automatically adds M2M authentication token to outgoing HTTP requests.
 * Useful for RestTemplate-based service-to-service communication. Only active when
 * Auth0 M2M is configured ({@code auth0.m2m.client-id}).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "auth0.m2m", name = "client-id")
@RequiredArgsConstructor
public class M2MAuthenticationInterceptor implements ClientHttpRequestInterceptor {

    private final CachedM2MAuthenticationService authenticationService;

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {

        // Add Authorization header with M2M token
        String authHeader = authenticationService.getAuthorizationHeader();
        request.getHeaders().add("Authorization", authHeader);

        log.debug("Added M2M authentication to request: {} {}", request.getMethod(), request.getURI());

        return execution.execute(request, body);
    }
}
