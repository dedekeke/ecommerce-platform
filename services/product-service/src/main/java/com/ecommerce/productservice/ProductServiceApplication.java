package com.ecommerce.productservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Product Service - Product catalog management service optimized for read-heavy workload.
 *
 * This service manages:
 * - Product catalog (CRUD operations)
 * - Category hierarchy management
 * - Product search and filtering
 * - Stock quantity tracking
 *
 * Uses MySQL for read-optimized performance with proper indexing.
 */
@SpringBootApplication(scanBasePackages = {
    "com.ecommerce.productservice",
    "com.ecommerce.common"
})
@EnableDiscoveryClient
@EnableCaching
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
