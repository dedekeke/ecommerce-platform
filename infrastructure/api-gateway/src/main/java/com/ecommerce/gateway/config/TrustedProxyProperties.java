package com.ecommerce.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Trusted reverse-proxy / load-balancer ranges, sourced from the environment
 * ({@code gateway.trusted-proxy.cidrs} &larr; {@code GATEWAY_TRUSTED_PROXIES}).
 *
 * <p>These CIDRs identify the ONLY peers whose {@code X-Forwarded-For} header the
 * gateway will believe when deriving the client IP for rate limiting. Anything
 * else (a direct client, an attacker) has its forwarding headers ignored — see
 * {@link ClientIpResolver}. Leaving this empty is the secure default: no proxy is
 * trusted, so {@code X-Forwarded-For} is never honored and the direct socket
 * address is always used.</p>
 */
@ConfigurationProperties(prefix = "gateway.trusted-proxy")
public class TrustedProxyProperties {

    /**
     * CIDR ranges (or single IPs, e.g. {@code 10.0.0.0/8}, {@code 172.16.0.1})
     * of the ingress/LB that fronts the gateway. Bound from a comma-separated
     * env value; empty means "trust no proxy".
     */
    private List<String> cidrs = new ArrayList<>();

    public List<String> getCidrs() {
        return cidrs;
    }

    public void setCidrs(List<String> cidrs) {
        this.cidrs = cidrs != null ? cidrs : new ArrayList<>();
    }
}
