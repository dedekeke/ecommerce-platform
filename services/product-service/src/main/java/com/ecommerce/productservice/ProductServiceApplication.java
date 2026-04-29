package com.ecommerce.productservice;

import com.ecommerce.common.security.config.BaseSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableAsync;

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
@SpringBootApplication
@ComponentScan(
    basePackages = {"com.ecommerce.productservice", "com.ecommerce.common"},
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = BaseSecurityConfig.class)
)
@EnableDiscoveryClient
@EnableAsync
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
