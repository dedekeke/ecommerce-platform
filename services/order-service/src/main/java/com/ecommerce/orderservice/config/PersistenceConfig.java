package com.ecommerce.orderservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Persistence/messaging/scheduling enablement.
 *
 * Kept separate from {@link com.ecommerce.orderservice.OrderServiceApplication}
 * so that sliced tests (e.g. {@code @WebMvcTest}) do not eagerly bootstrap JPA
 * repositories or Kafka, which would otherwise require an {@code entityManagerFactory}
 * that the slice does not provide.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.ecommerce.orderservice")
@EnableKafka
@EnableScheduling
public class PersistenceConfig {
}
