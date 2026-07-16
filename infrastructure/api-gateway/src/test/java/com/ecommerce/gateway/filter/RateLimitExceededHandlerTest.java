package com.ecommerce.gateway.filter;

import com.ecommerce.gateway.config.ClientIpResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link RateLimitExceededHandler} derives the logged client IP through
 * the shared {@link ClientIpResolver} rather than its own first-XFF logic.
 */
class RateLimitExceededHandlerTest {

    @Test
    void should_resolveClientIpViaResolver_when_rateLimited() {
        // Arrange
        ClientIpResolver resolver = mock(ClientIpResolver.class);
        when(resolver.resolve(any(ServerWebExchange.class))).thenReturn("198.51.100.7");
        RateLimitExceededHandler handler = new RateLimitExceededHandler(new ObjectMapper(), resolver);

        MockServerWebExchange exchange =
            MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders/guest"));
        // Downstream signals a rate-limit breach.
        WebFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return Mono.empty();
        };

        // Act
        handler.filter(exchange, chain).block();

        // Assert
        verify(resolver).resolve(exchange);
    }
}
