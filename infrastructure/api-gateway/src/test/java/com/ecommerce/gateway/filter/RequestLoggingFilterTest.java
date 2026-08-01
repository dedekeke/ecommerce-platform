package com.ecommerce.gateway.filter;

import com.ecommerce.gateway.config.ClientIpResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link RequestLoggingFilter} logs the client IP resolved through the
 * shared {@link ClientIpResolver} rather than the raw first-XFF entry.
 */
class RequestLoggingFilterTest {

    @Test
    void should_resolveClientIpViaResolver_when_loggingRequest() {
        // Arrange
        ClientIpResolver resolver = mock(ClientIpResolver.class);
        when(resolver.resolve(any(ServerWebExchange.class))).thenReturn("198.51.100.7");
        RequestLoggingFilter filter = new RequestLoggingFilter(resolver);
        ReflectionTestUtils.setField(filter, "loggingEnabled", true);
        ReflectionTestUtils.setField(filter, "includeHeaders", false);
        ReflectionTestUtils.setField(filter, "includeQueryParams", true);

        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/products").header("X-Forwarded-For", "1.2.3.4"));
        WebFilterChain chain = ex -> Mono.empty();

        // Act
        filter.filter(exchange, chain).block();

        // Assert
        verify(resolver, atLeastOnce()).resolve(any(ServerWebExchange.class));
    }
}
