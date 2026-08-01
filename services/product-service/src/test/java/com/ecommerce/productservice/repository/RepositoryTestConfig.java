package com.ecommerce.productservice.repository;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal Spring Boot configuration for {@code @DataJpaTest} repository slices.
 *
 * <p>Referencing this explicitly (via {@code @ContextConfiguration}) stops the
 * slice from climbing to {@code ProductServiceApplication}, whose explicit
 * {@code @ComponentScan(... "com.ecommerce.common")} would otherwise drag the
 * whole shared-library infrastructure (metrics, tracing, Kafka, M2M security)
 * into the slice — none of which a repository test needs. Only the JPA layer of
 * the productservice package is scanned here.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EnableJpaRepositories(basePackages = "com.ecommerce.productservice.repository")
@EntityScan(basePackages = "com.ecommerce.productservice.model")
class RepositoryTestConfig {
}
