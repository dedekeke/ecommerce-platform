package com.ecommerce.common.event.config;

import brave.Tracer;
import com.ecommerce.common.event.publisher.EventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Registers {@link EventPublisher} only when its Kafka and tracing collaborators
 * are present. Previously a {@code @Component}, it broke sliced tests that scan
 * {@code com.ecommerce.common} without a KafkaTemplate/Tracer.
 */
@AutoConfiguration
@ConditionalOnClass({KafkaTemplate.class, Tracer.class})
public class EventPublisherAutoConfiguration {

    @Bean
    @ConditionalOnBean({KafkaTemplate.class, Tracer.class})
    @ConditionalOnMissingBean(EventPublisher.class)
    public EventPublisher eventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            Tracer tracer) {
        return new EventPublisher(kafkaTemplate, objectMapper, tracer);
    }
}
