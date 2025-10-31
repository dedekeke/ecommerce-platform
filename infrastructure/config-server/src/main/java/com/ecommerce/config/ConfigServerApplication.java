package com.ecommerce.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Config Server Application for Centralized Configuration Management
 *
 * This server provides centralized configuration management for all microservices
 * in the e-commerce platform using Git as the backend storage.
 *
 * Features:
 * - Git-backed configuration storage
 * - Profile-based configuration (dev, docker, prod)
 * - Encryption for sensitive properties
 * - Service discovery integration with Eureka (auto-configured)
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
