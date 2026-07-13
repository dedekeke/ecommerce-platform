package com.ecommerce.gateway.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for Lore bug 4ebe00eb: the gateway turned a correctly-emitted
 * 401 into a client-visible 500.
 *
 * <p>Root cause: {@code SecurityHeadersFilter} mutated the response headers from
 * inside {@code .doOnSuccess(...)}, i.e. AFTER the downstream chain completed and
 * the response was already committed. Mutating committed (read-only) Netty
 * headers throws {@link UnsupportedOperationException}, which
 * {@code HttpServerOperations} then reports as a 500 — clobbering the real 401.
 * The same exception fired on 2xx responses (the body was merely already flushed,
 * so the client still saw 200 but the security headers were silently dropped).
 *
 * <p>Boots the full gateway with security ENABLED at RANDOM_PORT and drives real
 * HTTP through Netty via {@link WebTestClient}:
 * <ul>
 *   <li>a protected path with no token must yield 401 (not 500);</li>
 *   <li>a public 2xx route must still receive the security headers, proving the
 *       header writer no longer throws while touching a committed response.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.enabled=true",
        "security.ip-whitelist.enabled=false",
        "gateway.programmatic-routes.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.gateway.discovery.locator.enabled=false",
        "request-logging.enabled=false",
        "spring.data.redis.repositories.enabled=false",
        "management.tracing.enabled=false",
        // Lazy issuer resolution: the decoder only fetches metadata on first
        // decode, and no token is ever presented here, so boot never touches it.
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/issuer",
        "auth0.audience=test-audience",
        "AUTH0_ISSUER_URI=http://localhost/issuer",
        "AUTH0_DOMAIN=localhost",
        "AUTH0_AUDIENCE=test-audience",
        "AUTH0_CLIENT_ID=test",
        "AUTH0_CLIENT_SECRET=test",
        "REDIS_HOST=localhost",
        "REDIS_PORT=6379",
        "EUREKA_URI=http://localhost:8761/eureka",
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration," +
            "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration"
})
class CommittedResponseHeaderIntegrationTest {

    private static MockWebServer backend;

    @Autowired
    private WebTestClient webTestClient;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger rootLogger;

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

    @BeforeEach
    void attachLogAppender() {
        rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        logAppender = new ListAppender<>();
        logAppender.start();
        rootLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        rootLogger.detachAppender(logAppender);
    }

    @Test
    void should_return401_when_protectedPathAccessedWithoutToken() {
        webTestClient.get().uri("/api/orders")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void should_notThrowUnsupportedOperationException_when_mutatingCommittedResponse() {
        webTestClient.get().uri("/api/orders")
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(loggedUnsupportedOperationException())
                .as("SecurityHeadersFilter must not fail mutating a committed response")
                .isFalse();
    }

    @Test
    void should_attachSecurityHeaders_on2xxResponse() {
        backend.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        webTestClient.get().uri("/api/products")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().exists("X-Frame-Options");
    }

    private boolean loggedUnsupportedOperationException() {
        return logAppender.list.stream().anyMatch(event -> {
            IThrowableProxy proxy = event.getThrowableProxy();
            while (proxy != null) {
                if (UnsupportedOperationException.class.getName().equals(proxy.getClassName())) {
                    return true;
                }
                proxy = proxy.getCause();
            }
            return false;
        });
    }

    @TestConfiguration
    static class TestRoutes {

        @Bean
        RouteLocator testRouteLocator(RouteLocatorBuilder builder,
                                      @Value("${test.backend.uri}") String backendUri) {
            return builder.routes()
                    .route("test-public-products", r -> r
                            .path("/api/products/**")
                            .uri(backendUri))
                    .route("test-protected-orders", r -> r
                            .path("/api/orders/**")
                            .uri(backendUri))
                    .build();
        }
    }
}
