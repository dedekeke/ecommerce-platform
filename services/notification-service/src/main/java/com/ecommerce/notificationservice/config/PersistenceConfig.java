package com.ecommerce.notificationservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Mongo auditing, Kafka and scheduling enablement.
 *
 * Kept separate from {@link com.ecommerce.notificationservice.NotificationServiceApplication}
 * so sliced tests (e.g. {@code @WebMvcTest}) don't eagerly bootstrap Mongo
 * auditing, which would otherwise require a {@code mongoMappingContext} the slice
 * does not provide.
 */
@Configuration
@EnableMongoAuditing
@EnableKafka
@EnableScheduling
public class PersistenceConfig {
}
