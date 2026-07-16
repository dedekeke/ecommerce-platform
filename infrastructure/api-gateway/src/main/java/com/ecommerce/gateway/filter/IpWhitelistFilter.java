package com.ecommerce.gateway.filter;

import com.ecommerce.gateway.config.ClientIpResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Filter to restrict access to admin endpoints based on IP address.
 *
 * Features:
 * - Configurable IP whitelist
 * - Support for CIDR notation (e.g., 192.168.1.0/24)
 * - Support for localhost and private network ranges
 * - Configurable protected path patterns
 */
@Slf4j
@Component
public class IpWhitelistFilter implements WebFilter, Ordered {

    private static final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * Shared, spoofing-resistant client-IP resolver. Replaces the previous
     * private {@code getClientIp} that blindly trusted the first
     * {@code X-Forwarded-For} entry — which let any direct caller forge a
     * whitelisted source and slip past this admin-path guard.
     */
    private final ClientIpResolver clientIpResolver;

    @Value("${security.ip-whitelist.enabled:true}")
    private boolean whitelistEnabled;

    @Value("${security.ip-whitelist.addresses:127.0.0.1,::1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16}")
    private String whitelistAddresses;

    @Value("${security.ip-whitelist.protected-paths:/api/admin/**}")
    private String protectedPaths;

    private Set<String> whitelistedIps;
    private List<CidrRange> whitelistedRanges;
    private List<String> protectedPathPatterns;

    public IpWhitelistFilter(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @PostConstruct
    public void init() {
        whitelistedIps = new HashSet<>();
        whitelistedRanges = new java.util.ArrayList<>();

        String[] addresses = whitelistAddresses.split(",");
        for (String address : addresses) {
            address = address.trim();
            if (address.contains("/")) {
                try {
                    whitelistedRanges.add(new CidrRange(address));
                } catch (Exception e) {
                    log.warn("Invalid CIDR range: {}", address);
                }
            } else {
                whitelistedIps.add(address);
            }
        }

        protectedPathPatterns = Arrays.asList(protectedPaths.split(","));

        log.info("IP Whitelist filter initialized. Enabled: {}, IPs: {}, CIDR ranges: {}, Protected paths: {}",
            whitelistEnabled, whitelistedIps.size(), whitelistedRanges.size(), protectedPathPatterns);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 3;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!whitelistEnabled) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        boolean isProtected = protectedPathPatterns.stream()
            .anyMatch(pattern -> pathMatcher.match(pattern.trim(), path));

        if (!isProtected) {
            return chain.filter(exchange);
        }

        String clientIp = clientIpResolver.resolve(exchange);

        if (isWhitelisted(clientIp)) {
            log.debug("IP {} is whitelisted, allowing access to {}", clientIp, path);
            return chain.filter(exchange);
        }

        log.warn("IP {} is not whitelisted, blocking access to {}", clientIp, path);
        return handleForbidden(exchange);
    }

    private boolean isWhitelisted(String clientIp) {
        if (whitelistedIps.contains(clientIp)) {
            return true;
        }

        if ("0:0:0:0:0:0:0:1".equals(clientIp) || "::1".equals(clientIp)) {
            return whitelistedIps.contains("::1") || whitelistedIps.contains("127.0.0.1");
        }

        try {
            InetAddress clientAddress = InetAddress.getByName(clientIp);
            for (CidrRange range : whitelistedRanges) {
                if (range.contains(clientAddress)) {
                    return true;
                }
            }
        } catch (UnknownHostException e) {
            log.warn("Failed to parse client IP: {}", clientIp);
        }

        return false;
    }

    private Mono<Void> handleForbidden(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = """
            {
                "timestamp": "%s",
                "status": 403,
                "error": "Forbidden",
                "message": "Access denied. Your IP address is not authorized to access this resource.",
                "path": "%s"
            }
            """.formatted(
                java.time.Instant.now().toString(),
                exchange.getRequest().getPath().value()
            );

        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * Helper class to handle CIDR notation for IP ranges.
     */
    private static class CidrRange {
        private final byte[] networkAddress;
        private final int prefixLength;

        CidrRange(String cidr) throws UnknownHostException {
            String[] parts = cidr.split("/");
            this.networkAddress = InetAddress.getByName(parts[0]).getAddress();
            this.prefixLength = Integer.parseInt(parts[1]);
        }

        boolean contains(InetAddress address) {
            byte[] addressBytes = address.getAddress();

            if (addressBytes.length != networkAddress.length) {
                return false;
            }

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (addressBytes[i] != networkAddress[i]) {
                    return false;
                }
            }

            if (remainingBits > 0 && fullBytes < addressBytes.length) {
                int mask = 0xFF << (8 - remainingBits);
                if ((addressBytes[fullBytes] & mask) != (networkAddress[fullBytes] & mask)) {
                    return false;
                }
            }

            return true;
        }
    }
}
