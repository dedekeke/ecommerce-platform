package com.ecommerce.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.handler.DefaultWebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for Lore bug 1b8953dc.
 *
 * <p>The gateway is a JWT resource server. A transient Auth0 outage at boot must
 * NOT crash the gateway. The old code made an eager network call during startup
 * from {@link SecurityConfig#jwtDecoder()} via
 * {@code NimbusReactiveJwtDecoder.withIssuerLocation(issuer)} (which fetches the
 * OIDC metadata document during {@code build()}), and a vestigial
 * {@code spring.security.oauth2.client} registration whose {@code issuer-uri}
 * triggered OIDC discovery through {@code ReactiveOAuth2ClientAutoConfiguration}.
 * The fix makes the decoder lazy ({@code withJwkSetUri}) and removes the unused
 * OAuth2 client config + the {@code TokenRelay} default filter that required it.
 *
 * <p>This test points the resource-server issuer-uri at an unroutable blackhole
 * host (RFC 5737 TEST-NET-1, guaranteed never routable) and asserts:
 * <ol>
 *   <li>the context starts — no boot-time network call to the issuer;</li>
 *   <li>a {@link ReactiveJwtDecoder} bean is present — validation is still wired;</li>
 *   <li>an unauthenticated request to a protected path is denied with 401 by the
 *       {@link SecurityWebFilterChain} — validation is NOT weakened.</li>
 * </ol>
 *
 * <p>The 401 assertion drives the security filter chain directly with a
 * {@link MockServerWebExchange} rather than a live HTTP round-trip: the security
 * chain correctly emits 401, but on the live Netty server a separate,
 * pre-existing response-side filter mutates the committed response's read-only
 * headers and surfaces a 401 as a client-visible 500 (it also fires on 2xx
 * responses in other full-server gateway tests). That defect is unrelated to
 * this boot-crash fix and is tracked under its own ticket; asserting at the
 * filter-chain level proves the security decision without tripping it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // Security ENABLED so the JWT decoder bean is actually created at boot.
        "security.enabled=true",
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.gateway.discovery.locator.enabled=false",
        "request-logging.enabled=false",
        "security.ip-whitelist.enabled=false",
        "spring.data.redis.repositories.enabled=false",
        "management.tracing.enabled=false",
        // Blackhole issuer host (RFC 5737 TEST-NET-1, non-routable). If anything
        // tries to reach it at boot the context fails to start and this test fails.
        "AUTH0_ISSUER_URI=https://192.0.2.1/",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://192.0.2.1/",
        "AUTH0_DOMAIN=192.0.2.1",
        "AUTH0_AUDIENCE=test-audience",
        "REDIS_HOST=localhost",
        "REDIS_PORT=6379",
        "EUREKA_URI=http://localhost:8761/eureka",
        // Silence infra the test does not need. No OAuth2 *client* exclusion is
        // needed: production no longer configures an OAuth2 client at all
        // (NoOAuth2ClientConfigTest guards that), so there is no boot-time
        // discovery to suppress here.
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration," +
            "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration"
})
class UnreachableIssuerBootTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void should_startContext_when_issuerIsUnreachable() {
        assertThat(context).isNotNull();
    }

    @Test
    void should_wireJwtDecoder_when_securityEnabled() {
        assertThat(context.getBeansOfType(ReactiveJwtDecoder.class)).isNotEmpty();
    }

    @Test
    void should_denyUnauthenticatedRequest_atSecurityChain() {
        SecurityWebFilterChain securityChain = context.getBean(SecurityWebFilterChain.class);
        List<WebFilter> filters = securityChain.getWebFilters().collectList().block();
        assertThat(filters).isNotEmpty();

        // Downstream handler that records whether the request got past security.
        // For an unauthenticated protected path it must NOT be reached.
        AtomicBoolean reachedDownstream = new AtomicBoolean(false);
        WebHandler downstream = exchange -> Mono.fromRunnable(() -> reachedDownstream.set(true));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/123"));

        new DefaultWebFilterChain(downstream, filters).filter(exchange).block();

        assertThat(reachedDownstream)
                .as("unauthenticated request must be rejected before reaching downstream")
                .isFalse();
        assertThat(exchange.getResponse().getStatusCode())
                .as("missing-token request to a protected path must be 401")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
