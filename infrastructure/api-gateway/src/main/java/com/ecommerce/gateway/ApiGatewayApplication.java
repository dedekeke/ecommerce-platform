package com.ecommerce.gateway;

import com.ecommerce.gateway.aot.GatewayRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * API Gateway Application
 *
 * Spring Cloud Gateway with Auth0 integration for:
 * - Centralized API routing to all microservices
 * - JWT validation and authentication
 * - Token relay to downstream services
 * - CORS configuration for SPA frontends
 * - Rate limiting
 *
 * <p>{@code @ImportRuntimeHints} wires reflection metadata for GraalVM
 * native image builds (§2.5). It is a no-op on the JVM.
 */
@SpringBootApplication
@EnableDiscoveryClient
@ImportRuntimeHints(GatewayRuntimeHints.class)
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
