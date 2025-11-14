package com.ecommerce.productservice.event;

import com.ecommerce.productservice.model.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publisher for product-related events to Kafka.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventPublisher {

    private static final String PRODUCT_CREATED_TOPIC = "product.created";
    private static final String PRODUCT_UPDATED_TOPIC = "product.updated";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper eventObjectMapper;

    /**
     * Publish ProductCreatedEvent to Kafka.
     */
    public void publishProductCreated(Product product) {
        try {
            ProductCreatedEvent event = ProductCreatedEvent.builder()
                .productId(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .description(product.getDescription())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .price(product.getPrice())
                .currency(product.getCurrency())
                .stockQuantity(product.getStockQuantity())
                .active(product.getActive())
                .createdAt(product.getCreatedAt())
                .build();

            String eventJson = eventObjectMapper.writeValueAsString(event);
            log.info("Publishing ProductCreatedEvent for product ID: {}, SKU: {}", product.getId(), product.getSku());
            kafkaTemplate.send(PRODUCT_CREATED_TOPIC, product.getId().toString(), eventJson);
        } catch (Exception e) {
            log.error("Failed to publish ProductCreatedEvent for product ID: {}", product.getId(), e);
            throw new RuntimeException("Failed to publish product created event", e);
        }
    }

    /**
     * Publish ProductUpdatedEvent to Kafka.
     */
    public void publishProductUpdated(Product product) {
        try {
            ProductUpdatedEvent event = ProductUpdatedEvent.builder()
                .productId(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .description(product.getDescription())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .price(product.getPrice())
                .currency(product.getCurrency())
                .stockQuantity(product.getStockQuantity())
                .active(product.getActive())
                .updatedAt(product.getUpdatedAt())
                .build();

            String eventJson = eventObjectMapper.writeValueAsString(event);
            log.info("Publishing ProductUpdatedEvent for product ID: {}, SKU: {}", product.getId(), product.getSku());
            kafkaTemplate.send(PRODUCT_UPDATED_TOPIC, product.getId().toString(), eventJson);
        } catch (Exception e) {
            log.error("Failed to publish ProductUpdatedEvent for product ID: {}", product.getId(), e);
            throw new RuntimeException("Failed to publish product updated event", e);
        }
    }
}
