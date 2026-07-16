package com.ecommerce.gateway.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ClientIpResolver} — the X-Forwarded-For anti-spoofing
 * logic that backs the guest-endpoint rate limits.
 */
class ClientIpResolverTest {

    private static ClientIpResolver resolverTrusting(String... cidrs) {
        TrustedProxyProperties props = new TrustedProxyProperties();
        props.setCidrs(List.of(cidrs));
        return new ClientIpResolver(props);
    }

    @Test
    void should_ignoreSpoofedXff_when_peerIsUntrusted() {
        // Arrange: no proxy trusted; a direct caller forges an XFF.
        ClientIpResolver resolver = resolverTrusting();

        // Act
        String key = resolver.resolveClientIp("203.0.113.9", "1.2.3.4");

        // Assert: the forged header is ignored — key is the real socket address.
        assertThat(key).isEqualTo("203.0.113.9");
    }

    @Test
    void should_ignoreSpoofedXff_when_peerNotInConfiguredProxyRange() {
        // Arrange: a proxy range is configured, but the peer is NOT in it.
        ClientIpResolver resolver = resolverTrusting("10.0.0.0/8");

        // Act
        String key = resolver.resolveClientIp("203.0.113.9", "1.2.3.4, 5.6.7.8");

        // Assert
        assertThat(key).isEqualTo("203.0.113.9");
    }

    @Test
    void should_honorXff_when_peerIsTrustedProxy() {
        // Arrange: request arrives from the trusted LB, which set XFF to the client.
        ClientIpResolver resolver = resolverTrusting("10.0.0.0/8");

        // Act
        String key = resolver.resolveClientIp("10.1.2.3", "198.51.100.7");

        // Assert: the client IP from the trusted proxy is honored.
        assertThat(key).isEqualTo("198.51.100.7");
    }

    @Test
    void should_returnRightmostUntrustedHop_when_chainHasMultipleProxies() {
        // Arrange: client -> external hop -> our trusted proxies (10.x).
        // The rightmost NON-trusted entry is the real edge client we can attest.
        ClientIpResolver resolver = resolverTrusting("10.0.0.0/8");

        // Act: attacker prepends a fake "1.1.1.1"; real client is 198.51.100.7.
        String key = resolver.resolveClientIp("10.0.0.5", "1.1.1.1, 198.51.100.7, 10.0.0.9");

        // Assert: fake left-most entry cannot be reached — rightmost-untrusted wins.
        assertThat(key).isEqualTo("198.51.100.7");
    }

    @Test
    void should_fallBackToPeer_when_trustedPeerSendsNoXff() {
        ClientIpResolver resolver = resolverTrusting("10.0.0.0/8");

        String key = resolver.resolveClientIp("10.1.2.3", null);

        assertThat(key).isEqualTo("10.1.2.3");
    }

    @Test
    void should_fallBackToPeer_when_everyHopIsTrusted() {
        ClientIpResolver resolver = resolverTrusting("10.0.0.0/8");

        String key = resolver.resolveClientIp("10.0.0.5", "10.0.0.9, 10.0.0.8");

        assertThat(key).isEqualTo("10.0.0.5");
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "not-an-ip", "999.999.999.999"})
    void should_ignoreNonIpXffTokens_when_peerIsTrusted(String forged) {
        // Arrange: attacker injects garbage after our proxy; it must not match a
        // trusted range and must not trigger DNS resolution.
        ClientIpResolver resolver = resolverTrusting("10.0.0.0/8");

        // Act: garbage is the rightmost-untrusted "hop" — returned verbatim as the
        // key (still bounded to that single bucket, never a real other IP).
        String key = resolver.resolveClientIp("10.0.0.5", forged);

        // Assert
        assertThat(key).isEqualTo(forged);
    }

    @Test
    void should_returnUnknown_when_noRemoteAddressAndUntrusted() {
        ClientIpResolver resolver = resolverTrusting();

        String key = resolver.resolveClientIp(null, "1.2.3.4");

        assertThat(key).isEqualTo("unknown");
    }

    @Test
    void should_supportSingleIpTrustedProxy() {
        ClientIpResolver resolver = resolverTrusting("172.16.0.1");

        String honored = resolver.resolveClientIp("172.16.0.1", "198.51.100.7");
        String ignored = resolver.resolveClientIp("172.16.0.2", "198.51.100.7");

        assertThat(honored).isEqualTo("198.51.100.7");
        assertThat(ignored).isEqualTo("172.16.0.2");
    }

    // --- IPv6 coverage -------------------------------------------------------

    @Test
    void should_honorIpv6Client_when_peerIsInTrustedIpv6Slash64() {
        // Arrange: proxy peer inside a trusted IPv6 /64 forwards an IPv6 client.
        ClientIpResolver resolver = resolverTrusting("2001:db8:abcd:1::/64");

        // Act
        String key = resolver.resolveClientIp("2001:db8:abcd:1::5", "2001:db8:ffff::9");

        // Assert
        assertThat(key).isEqualTo("2001:db8:ffff::9");
    }

    @Test
    void should_ignoreSpoofedIpv6Xff_when_ipv6PeerOutsideTrustedSlash64() {
        // Arrange: /64 differs in the 4th group -> peer is NOT a trusted proxy.
        ClientIpResolver resolver = resolverTrusting("2001:db8:abcd:1::/64");

        // Act: attacker forges a whitelisted-looking IPv6 XFF.
        String key = resolver.resolveClientIp("2001:db8:abcd:2::1", "2001:db8:abcd:1::99");

        // Assert: forged header ignored; the real IPv6 socket address is used.
        assertThat(key).isEqualTo("2001:db8:abcd:2::1");
    }

    @Test
    void should_honorIpv6Client_when_trustedIpv4ProxyForwardsIpv6() {
        // Arrange: mixed stack — IPv4 trusted proxy fronting an IPv6 client.
        ClientIpResolver resolver = resolverTrusting("10.0.0.0/8");

        // Act
        String key = resolver.resolveClientIp("10.0.0.5", "2001:db8::42");

        // Assert
        assertThat(key).isEqualTo("2001:db8::42");
    }

    @Test
    void should_returnRightmostUntrustedIpv6Hop_when_chainMixesTrustedIpv6Proxies() {
        // Arrange: client -> external IPv6 hop -> our trusted IPv6 /64 proxies.
        ClientIpResolver resolver = resolverTrusting("2001:db8:abcd:1::/64");

        // Act
        String key = resolver.resolveClientIp(
            "2001:db8:abcd:1::5", "2001:db8:ffff::7, 2001:db8:abcd:1::9");

        // Assert: rightmost non-trusted hop is the attributable client.
        assertThat(key).isEqualTo("2001:db8:ffff::7");
    }

    @Test
    void should_notMatchIpv4CandidateAgainstIpv6Range_when_familiesDiffer() {
        // Arrange: only an IPv6 range is trusted; an IPv4 peer must not match it.
        ClientIpResolver resolver = resolverTrusting("2001:db8:abcd:1::/64");

        // Act
        String key = resolver.resolveClientIp("203.0.113.9", "2001:db8:abcd:1::99");

        // Assert: family mismatch -> peer untrusted -> forged header ignored.
        assertThat(key).isEqualTo("203.0.113.9");
    }
}
