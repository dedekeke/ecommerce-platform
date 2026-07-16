package com.ecommerce.gateway.filter;

import com.ecommerce.gateway.config.ClientIpResolver;
import com.ecommerce.gateway.config.TrustedProxyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security regression tests for {@link IpWhitelistFilter}.
 *
 * <p>The filter previously derived the client IP from the first
 * {@code X-Forwarded-For} entry, so any direct caller could send
 * {@code X-Forwarded-For: <whitelisted-ip>} and walk past the admin-path guard.
 * These tests lock in the fix: the whitelist is now evaluated against the IP
 * resolved by {@link ClientIpResolver}, which believes {@code X-Forwarded-For}
 * only when the immediate peer is a configured trusted proxy.</p>
 */
class IpWhitelistFilterTest {

    private static final String PROTECTED_PATH = "/api/admin/users";
    private static final String WHITELIST = "127.0.0.1,::1,10.0.0.0/8";

    /** A whitelisted client IP an attacker would try to forge into X-Forwarded-For. */
    private static final String WHITELISTED_CLIENT = "10.0.0.5";

    private IpWhitelistFilter filterTrusting(String... trustedProxyCidrs) {
        TrustedProxyProperties props = new TrustedProxyProperties();
        props.setCidrs(List.of(trustedProxyCidrs));
        IpWhitelistFilter filter = new IpWhitelistFilter(new ClientIpResolver(props));
        ReflectionTestUtils.setField(filter, "whitelistEnabled", true);
        ReflectionTestUtils.setField(filter, "whitelistAddresses", WHITELIST);
        ReflectionTestUtils.setField(filter, "protectedPaths", "/api/admin/**");
        filter.init();
        return filter;
    }

    private static MockServerWebExchange exchange(String peer, String path, String xff) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get(path)
            .remoteAddress(new InetSocketAddress(peer, 0));
        if (xff != null) {
            builder.header("X-Forwarded-For", xff);
        }
        return MockServerWebExchange.from(builder.build());
    }

    @Test
    void should_blockSpoofedWhitelistedIp_when_peerIsUntrusted() {
        // Arrange: no proxy is trusted; attacker peer forges a whitelisted XFF.
        IpWhitelistFilter filter = filterTrusting();
        MockServerWebExchange exchange = exchange("203.0.113.9", PROTECTED_PATH, WHITELISTED_CLIENT);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        // Act
        filter.filter(exchange, chain).block();

        // Assert: the forged header is ignored, the real socket addr is not
        // whitelisted, so the request is rejected and never reaches the chain.
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chainInvoked).isFalse();
    }

    @Test
    void should_honorForwardedClient_when_peerIsTrustedProxy() {
        // Arrange: request arrives via a trusted LB (172.16.0.1) that forwarded a
        // whitelisted client (10.0.0.5).
        IpWhitelistFilter filter = filterTrusting("172.16.0.0/12");
        MockServerWebExchange exchange = exchange("172.16.0.1", PROTECTED_PATH, WHITELISTED_CLIENT);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        // Act
        filter.filter(exchange, chain).block();

        // Assert: the forwarded client is trusted and whitelisted -> allowed.
        assertThat(chainInvoked).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void should_blockForwardedClient_when_trustedProxyForwardsNonWhitelistedIp() {
        // Arrange: trusted LB forwards a NON-whitelisted external client.
        IpWhitelistFilter filter = filterTrusting("172.16.0.0/12");
        MockServerWebExchange exchange = exchange("172.16.0.1", PROTECTED_PATH, "198.51.100.7");
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        // Act
        filter.filter(exchange, chain).block();

        // Assert
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chainInvoked).isFalse();
    }

    @Test
    void should_allowDirectWhitelistedClient_when_noForwardingHeader() {
        // Arrange: direct request from a whitelisted socket address, no XFF.
        IpWhitelistFilter filter = filterTrusting();
        MockServerWebExchange exchange = exchange("127.0.0.1", PROTECTED_PATH, null);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        // Act
        filter.filter(exchange, chain).block();

        // Assert
        assertThat(chainInvoked).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void should_skipFilter_when_pathNotProtected() {
        // Arrange: untrusted peer forging a whitelisted XFF, but on a public path.
        IpWhitelistFilter filter = filterTrusting();
        MockServerWebExchange exchange = exchange("203.0.113.9", "/api/products", WHITELISTED_CLIENT);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        // Act
        filter.filter(exchange, chain).block();

        // Assert: non-admin paths are untouched by the whitelist guard.
        assertThat(chainInvoked).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }
}
