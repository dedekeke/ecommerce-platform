package com.ecommerce.gateway.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Companion regression test to {@link CommittedResponseHeaderIntegrationTest}
 * for the OTHER response-committing short-circuits that sit downstream of
 * {@code SecurityHeadersFilter} in the same {@code WebFilter} chain:
 *
 * <ul>
 *   <li>{@code IpWhitelistFilter} — commits a 403 via {@code writeWith(...)};</li>
 *   <li>{@code RateLimitExceededHandler} — commits a 429 JSON body.</li>
 * </ul>
 *
 * <p>{@code SecurityHeadersFilter} runs at {@code HIGHEST_PRECEDENCE}, strictly
 * outermost, so its {@code beforeCommit} hook must fire cleanly regardless of
 * which downstream component commits the response. Security itself is disabled
 * here so the 401 path (covered elsewhere) does not pre-empt these codes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.enabled=false",
        "gateway.programmatic-routes.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.gateway.discovery.locator.enabled=false",
        "request-logging.enabled=false",
        "spring.data.redis.repositories.enabled=false",
        "management.tracing.enabled=false",
        // Force the IP whitelist to reject the loopback client WebTestClient uses,
        // so /api/admin/** short-circuits to a committed 403.
        "security.ip-whitelist.enabled=true",
        "security.ip-whitelist.protected-paths=/api/admin/**",
        "security.ip-whitelist.addresses=10.0.0.0/8",
        "AUTH0_ISSUER_URI=http://localhost/issuer",
        "AUTH0_DOMAIN=localhost",
        "AUTH0_AUDIENCE=test-audience",
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
class ShortCircuitResponseHeaderIntegrationTest {

    private static final String COMMIT_LOGGER =
            "org.springframework.web.server.adapter.HttpWebHandlerAdapter";

    @Autowired
    private WebTestClient webTestClient;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger commitLogger;

    @BeforeEach
    void attachLogAppender() {
        commitLogger = (Logger) LoggerFactory.getLogger(COMMIT_LOGGER);
        logAppender = new ListAppender<>();
        logAppender.start();
        commitLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        commitLogger.detachAppender(logAppender);
    }

    @Test
    void should_return403WithSecurityHeaders_when_ipNotWhitelisted() {
        webTestClient.get().uri("/api/admin/dashboard")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().exists("Content-Security-Policy")
                .expectHeader().exists("Permissions-Policy");

        assertThat(loggedUnsupportedOperationException()).isFalse();
    }

    @Test
    void should_return429WithSecurityHeaders_when_rateLimitExceeded() {
        webTestClient.get().uri("/api/rate-limited/resource")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader().exists("Content-Security-Policy")
                .expectHeader().exists("Permissions-Policy");

        assertThat(loggedUnsupportedOperationException()).isFalse();
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
    static class RateLimitSimulatorConfig {

        /**
         * Stands in for Spring Cloud Gateway's {@code RequestRateLimiter} (which
         * needs Redis): short-circuits {@code /api/rate-limited/**} by setting a
         * 429 status without writing a body, so the outer
         * {@code RateLimitExceededHandler} rewrites it into the JSON 429. Runs at
         * the default (lowest) precedence, i.e. innermost, so it is downstream of
         * the handler it feeds.
         */
        @Bean
        WebFilter rateLimitSimulator() {
            return (exchange, chain) -> {
                if (exchange.getRequest().getPath().value().startsWith("/api/rate-limited")) {
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    return Mono.empty();
                }
                return chain.filter(exchange);
            };
        }
    }
}
