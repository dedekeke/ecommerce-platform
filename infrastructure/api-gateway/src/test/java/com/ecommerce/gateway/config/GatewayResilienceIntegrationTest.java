package com.ecommerce.gateway.config;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resilience contract for the gateway's outbound HTTP behaviour
 * (Scalability P1, Lore 8b89f13d).
 *
 * <p>Boots the gateway (security disabled, no Eureka/Redis) and drives real
 * HTTP through it via {@link WebTestClient} against a {@link MockWebServer}
 * backend, exercising the actual Spring Cloud Gateway {@code response-timeout}
 * and {@code Retry} filter machinery end-to-end.
 *
 * <p>The global {@code response-timeout} is overridden to a small value here so
 * the timeout path fires quickly — production uses 5s (see application.yml).
 * The test routes replicate the production filter policy: GET-only retry, no
 * POST retry.
 *
 * <p>Contract asserted:
 * <ul>
 *   <li>a downstream that never responds in time yields {@code 504 GATEWAY_TIMEOUT}
 *       instead of hanging (holding the gateway connection open);</li>
 *   <li>a GET that times out is NOT retried (TimeoutException dropped from the
 *       retryable set) — the downstream is hit exactly once, no
 *       (retries+1)x amplification against an already-hung service;</li>
 *   <li>a POST to a route returning {@code 503} is NOT retried — the downstream
 *       is hit exactly once (no duplicate side-effects, no load amplification);</li>
 *   <li>a GET returning {@code 503} then {@code 200} IS retried until it succeeds.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // Short response-timeout keeps the timeout assertion fast; prod uses 5s.
        "spring.cloud.gateway.httpclient.response-timeout=800ms",
        "security.enabled=false",
        "gateway.programmatic-routes.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.gateway.discovery.locator.enabled=false",
        "request-logging.enabled=false",
        "security.ip-whitelist.enabled=false",
        "spring.data.redis.repositories.enabled=false",
        "management.tracing.enabled=false",
        "AUTH0_ISSUER_URI=http://localhost/issuer",
        "AUTH0_DOMAIN=localhost",
        "AUTH0_AUDIENCE=test",
        "AUTH0_CLIENT_ID=test",
        "AUTH0_CLIENT_SECRET=test",
        "REDIS_HOST=localhost",
        "REDIS_PORT=6379",
        "EUREKA_URI=http://localhost:8761/eureka",
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration," +
            "org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration," +
            "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration"
})
class GatewayResilienceIntegrationTest {

    private static MockWebServer backend;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void startBackend() throws IOException {
        backend = new MockWebServer();
        backend.start();
    }

    @AfterAll
    static void stopBackend() throws IOException {
        backend.shutdown();
    }

    @DynamicPropertySource
    static void backendUri(DynamicPropertyRegistry registry) {
        registry.add("test.backend.uri", () -> "http://localhost:" + backend.getPort());
    }

    @Test
    void should_return504_when_downstreamExceedsResponseTimeout() {
        // Backend never sends headers within the 800ms response-timeout window.
        backend.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("late")
                .setHeadersDelay(5, TimeUnit.SECONDS));

        webTestClient.get().uri("/timeout-test/ping")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void should_notRetryGet_when_downstreamTimesOut() {
        int before = backend.getRequestCount();
        // Route has GET retry enabled, but the downstream stalls past the
        // response-timeout rather than returning a retryable 503.
        backend.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("late")
                .setHeadersDelay(5, TimeUnit.SECONDS));

        webTestClient.get().uri("/retry-test/orders")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT);

        // TimeoutException is excluded from the retryable set → exactly one hit,
        // no (retries+1)x amplification against a hung downstream.
        assertThat(backend.getRequestCount() - before).isEqualTo(1);
    }

    @Test
    void should_notRetryPost_when_downstreamReturns503() {
        int before = backend.getRequestCount();
        backend.enqueue(new MockResponse().setResponseCode(503));

        webTestClient.post().uri("/retry-test/orders")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        // POST is not in the retry method set → downstream hit exactly once.
        assertThat(backend.getRequestCount() - before).isEqualTo(1);
    }

    @Test
    void should_retryGet_when_downstreamReturns503ThenSucceeds() {
        int before = backend.getRequestCount();
        backend.enqueue(new MockResponse().setResponseCode(503));
        backend.enqueue(new MockResponse().setResponseCode(503));
        backend.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        webTestClient.get().uri("/retry-test/orders")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("ok");

        // Two 503s were retried before the 200 → three downstream hits total.
        assertThat(backend.getRequestCount() - before).isEqualTo(3);
    }

    @TestConfiguration
    static class TestRoutes {

        @Bean
        RouteLocator resilienceTestRoutes(RouteLocatorBuilder builder,
                                          @Value("${test.backend.uri}") String backendUri) {
            return builder.routes()
                    // No retry filter — asserts the raw response-timeout path.
                    .route("timeout-test", r -> r
                            .path("/timeout-test/**")
                            .uri(backendUri))
                    // Mirrors the production default-filter Retry policy:
                    // GET only, 502/503 status retry, exceptions pinned to
                    // IOException (TimeoutException dropped so a response-timeout
                    // is NOT retried).
                    .route("retry-test", r -> r
                            .path("/retry-test/**")
                            .filters(f -> f.retry(config -> {
                                config.setRetries(3);
                                config.setMethods(HttpMethod.GET);
                                config.setStatuses(HttpStatus.SERVICE_UNAVAILABLE);
                                config.setExceptions(java.io.IOException.class);
                                config.setBackoff(Duration.ofMillis(1), Duration.ofMillis(5), 2, false);
                            }))
                            .uri(backendUri))
                    .build();
        }
    }
}
