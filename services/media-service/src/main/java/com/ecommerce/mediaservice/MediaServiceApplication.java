package com.ecommerce.mediaservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Media Service Application
 *
 * Provides image and file management capabilities including:
 * - File upload with validation
 * - Image resizing and thumbnail generation
 * - File storage and retrieval
 * - Access control for file operations
 */
@SpringBootApplication
@EnableDiscoveryClient
public class MediaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediaServiceApplication.class, args);
    }
}
