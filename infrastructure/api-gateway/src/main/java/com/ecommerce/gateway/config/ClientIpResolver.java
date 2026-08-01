package com.ecommerce.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Resolves the real client IP used as the rate-limit key, defending against
 * {@code X-Forwarded-For} spoofing.
 *
 * <p>{@code X-Forwarded-For} is client-controlled: a direct caller can send any
 * value and forge a different rate-limit bucket per request, defeating the limit
 * entirely — which matters here because this resolver backs the sole rate-limit
 * defense on the UNAUTHENTICATED guest endpoints ({@code /api/orders/guest},
 * {@code /api/cart/guest/**}).</p>
 *
 * <p>The header is therefore only believed when the <b>immediate peer</b> (the
 * TCP socket address) is a configured trusted proxy ({@link TrustedProxyProperties}).
 * When trusted, the chain is walked from the RIGHT and the first entry that is
 * NOT itself a trusted proxy is taken as the client — the rightmost-untrusted
 * hop, which an external caller cannot control past our own proxy layer. When the
 * peer is untrusted (or no proxy is configured), the forwarding headers are
 * ignored entirely and the direct socket address is used.</p>
 *
 * <p>CIDR matching is implemented in-module ({@link CidrRange}) rather than via
 * {@code IpAddressMatcher}: the latter's API references the servlet
 * {@code HttpServletRequest}, which is absent from this reactive/WebFlux gateway.
 * The matcher parses IPv4 literals manually and treats {@code :}-bearing tokens
 * as IPv6 literals, so an attacker-supplied hostname can never trigger a DNS
 * lookup.</p>
 */
@Slf4j
@Component
public class ClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String UNKNOWN = "unknown";

    private final List<CidrRange> trustedProxies;

    public ClientIpResolver(TrustedProxyProperties properties) {
        this.trustedProxies = properties.getCidrs().stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .map(CidrRange::parse)
            .toList();
        log.info("ClientIpResolver initialised with {} trusted proxy range(s)", trustedProxies.size());
    }

    /** Resolve the client IP for the current request. */
    public String resolve(ServerWebExchange exchange) {
        String remoteAddr = remoteAddress(exchange);
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst(X_FORWARDED_FOR);
        return resolveClientIp(remoteAddr, xForwardedFor);
    }

    /**
     * Pure resolution logic (no exchange dependency) so it is trivially unit
     * testable: given the direct socket address and the raw {@code X-Forwarded-For}
     * header, return the IP to rate-limit on.
     */
    public String resolveClientIp(String remoteAddr, String xForwardedFor) {
        String peer = StringUtils.hasText(remoteAddr) ? remoteAddr.trim() : UNKNOWN;

        // Untrusted immediate peer: the forwarding header is attacker-controlled,
        // so ignore it and rate-limit on the real socket address.
        if (!isTrustedProxy(peer)) {
            return peer;
        }
        if (!StringUtils.hasText(xForwardedFor)) {
            return peer;
        }

        // Trusted peer: walk the chain right-to-left and return the first hop that
        // is NOT one of our own trusted proxies — the true external client.
        String[] hops = xForwardedFor.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (hop.isEmpty()) {
                continue;
            }
            if (!isTrustedProxy(hop)) {
                return hop;
            }
        }
        // Every hop was a trusted proxy (or blank): fall back to the peer.
        return peer;
    }

    private boolean isTrustedProxy(String candidate) {
        for (CidrRange range : trustedProxies) {
            if (range.matches(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String remoteAddress(ServerWebExchange exchange) {
        var remote = exchange.getRequest().getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return UNKNOWN;
        }
        return remote.getAddress().getHostAddress();
    }

    /**
     * A single CIDR range (or a bare IP treated as a /32 or /128). Parses and
     * matches IP literals only — never resolves hostnames, so hostile input in a
     * forwarding header cannot cause a DNS lookup.
     */
    static final class CidrRange {

        private final byte[] network;
        private final int prefixBits;

        private CidrRange(byte[] network, int prefixBits) {
            this.network = network;
            this.prefixBits = prefixBits;
        }

        static CidrRange parse(String cidr) {
            int slash = cidr.indexOf('/');
            String ipPart = slash >= 0 ? cidr.substring(0, slash).trim() : cidr.trim();
            byte[] addr = ipLiteralToBytes(ipPart);
            int maxBits = addr.length * 8;
            int bits = maxBits;
            if (slash >= 0) {
                bits = Integer.parseInt(cidr.substring(slash + 1).trim());
                if (bits < 0 || bits > maxBits) {
                    throw new IllegalArgumentException("Invalid CIDR prefix length: " + cidr);
                }
            }
            return new CidrRange(addr, bits);
        }

        boolean matches(String candidate) {
            byte[] addr;
            try {
                addr = ipLiteralToBytes(candidate);
            } catch (IllegalArgumentException notAnIp) {
                return false;
            }
            if (addr.length != network.length) {
                // IPv4 candidate against an IPv6 range (or vice-versa): no match.
                return false;
            }
            int fullBytes = prefixBits / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (addr[i] != network[i]) {
                    return false;
                }
            }
            int remainingBits = prefixBits % 8;
            if (remainingBits > 0) {
                int mask = (0xFF << (8 - remainingBits)) & 0xFF;
                return (addr[fullBytes] & mask) == (network[fullBytes] & mask);
            }
            return true;
        }

        /**
         * Convert an IP literal to its byte form WITHOUT DNS resolution. IPv4 is
         * parsed by hand (rejecting octets &gt; 255, so {@code 999.999.999.999}
         * cannot leak into a lookup); {@code :}-bearing tokens are IPv6 literals
         * (never valid hostnames). Anything else throws {@link IllegalArgumentException}.
         */
        private static byte[] ipLiteralToBytes(String value) {
            if (!StringUtils.hasText(value)) {
                throw new IllegalArgumentException("Blank IP");
            }
            String s = value.trim();
            if (s.startsWith("[") && s.endsWith("]") && s.length() > 2) {
                s = s.substring(1, s.length() - 1);
            }
            if (s.indexOf(':') >= 0) {
                try {
                    return InetAddress.getByName(s).getAddress();
                } catch (UnknownHostException ex) {
                    throw new IllegalArgumentException("Invalid IPv6 literal: " + value, ex);
                }
            }
            String[] octets = s.split("\\.", -1);
            if (octets.length != 4) {
                throw new IllegalArgumentException("Not an IPv4 literal: " + value);
            }
            byte[] out = new byte[4];
            for (int i = 0; i < 4; i++) {
                String octet = octets[i];
                if (octet.isEmpty() || octet.length() > 3) {
                    throw new IllegalArgumentException("Invalid IPv4 octet in: " + value);
                }
                for (int c = 0; c < octet.length(); c++) {
                    if (octet.charAt(c) < '0' || octet.charAt(c) > '9') {
                        throw new IllegalArgumentException("Invalid IPv4 octet in: " + value);
                    }
                }
                int parsed = Integer.parseInt(octet);
                if (parsed > 255) {
                    throw new IllegalArgumentException("IPv4 octet out of range in: " + value);
                }
                out[i] = (byte) parsed;
            }
            return out;
        }
    }
}
