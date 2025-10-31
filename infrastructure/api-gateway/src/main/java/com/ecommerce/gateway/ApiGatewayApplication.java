package com.ecommerce.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API Gateway Application
 *
 * Spring Cloud Gateway with Auth0 integration for:
 * - Centralized API routing to all microservices
 * - JWT validation and authentication
 * - Token relay to downstream services
 * - CORS configuration for SPA frontends
 * - Rate limiting
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
