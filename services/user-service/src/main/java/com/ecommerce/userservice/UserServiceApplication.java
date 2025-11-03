package com.ecommerce.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * User Service Application
 *
 * Handles user management, profiles, and Auth0 synchronization.
 *
 * Features:
 * - User profile management
 * - Address management
 * - Auth0 user synchronization on first login
 * - User preferences
 *
 * @author E-Commerce Platform Team
 */
@SpringBootApplication(scanBasePackages = {
    "com.ecommerce.userservice",
    "com.ecommerce.common"
})
@EnableDiscoveryClient
@EnableJpaAuditing
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }

}
