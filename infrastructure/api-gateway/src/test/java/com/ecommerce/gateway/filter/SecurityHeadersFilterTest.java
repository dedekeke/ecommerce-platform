package com.ecommerce.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolated unit tests for {@link SecurityHeadersFilter}, driving the filter
 * directly against a {@link MockServerWebExchange} without Spring Security's
 * default header writer in the mix. This locks in the filter's OWN contract —
 * which headers it emits, their values, the config toggles, and the
 * {@code containsKey} idempotency guards — independently of the full-boot
 * integration tests.
 *
 * <p>The chain commits the response ({@code setComplete()}), which fires the
 * filter's {@code beforeCommit} hook; assertions then read the resulting headers.
 */
class SecurityHeadersFilterTest {

    /** Chain that commits the response, triggering registered beforeCommit hooks. */
    private static final WebFilterChain COMMITTING_CHAIN = ex -> ex.getResponse().setComplete();

    private SecurityHeadersFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SecurityHeadersFilter();
        ReflectionTestUtils.setField(filter, "hstsEnabled", true);
        ReflectionTestUtils.setField(filter, "hstsMaxAge", 31536000L);
        ReflectionTestUtils.setField(filter, "cspEnabled", true);
        ReflectionTestUtils.setField(filter, "frameOptions", "DENY");
    }

    private HttpHeaders runFilter(MockServerWebExchange exchange) {
        filter.filter(exchange, COMMITTING_CHAIN).block();
        return exchange.getResponse().getHeaders();
    }

    private static MockServerWebExchange apiExchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders"));
    }

    @Test
    void should_setContentSecurityPolicy_when_cspEnabled() {
        HttpHeaders headers = runFilter(apiExchange());

        assertThat(headers.getFirst("Content-Security-Policy")).contains("default-src 'self'");
    }

    @Test
    void should_setPermissionsPolicy_always() {
        HttpHeaders headers = runFilter(apiExchange());

        assertThat(headers.getFirst("Permissions-Policy")).contains("geolocation=()");
    }

    @Test
    void should_setHsts_when_hstsEnabled() {
        HttpHeaders headers = runFilter(apiExchange());

        assertThat(headers.getFirst("Strict-Transport-Security")).contains("max-age=31536000");
    }

    @Test
    void should_setCoreOwaspHeaders_always() {
        HttpHeaders headers = runFilter(apiExchange());

        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("X-XSS-Protection")).isEqualTo("1; mode=block");
        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
    }

    @Test
    void should_setNoStoreCacheControl_when_apiPath() {
        HttpHeaders headers = runFilter(apiExchange());

        assertThat(headers.getFirst("Cache-Control")).contains("no-store");
        assertThat(headers.getFirst("Pragma")).isEqualTo("no-cache");
    }

    @Test
    void should_notSetCacheControl_when_nonApiPath() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health"));

        HttpHeaders headers = runFilter(exchange);

        assertThat(headers.containsKey("Cache-Control")).isFalse();
    }

    @Test
    void should_omitCsp_when_cspDisabled() {
        ReflectionTestUtils.setField(filter, "cspEnabled", false);

        HttpHeaders headers = runFilter(apiExchange());

        assertThat(headers.containsKey("Content-Security-Policy")).isFalse();
    }

    @Test
    void should_omitHsts_when_hstsDisabled() {
        ReflectionTestUtils.setField(filter, "hstsEnabled", false);

        HttpHeaders headers = runFilter(apiExchange());

        assertThat(headers.containsKey("Strict-Transport-Security")).isFalse();
    }

    @Test
    void should_notOverwriteExistingHeader_when_alreadyPresent() {
        MockServerWebExchange exchange = apiExchange();
        exchange.getResponse().getHeaders().add("X-Frame-Options", "SAMEORIGIN");

        HttpHeaders headers = runFilter(exchange);

        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("SAMEORIGIN");
    }
}
