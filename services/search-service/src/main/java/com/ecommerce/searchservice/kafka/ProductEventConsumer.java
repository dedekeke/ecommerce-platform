package com.ecommerce.searchservice.kafka;

import com.ecommerce.searchservice.document.ProductDocument;
import com.ecommerce.searchservice.event.ProductEvent;
import com.ecommerce.searchservice.service.ProductSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Product Event Consumer
 * Listens to product events and updates Elasticsearch index
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProductEventConsumer {

    private final ProductSearchService searchService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "product.created", groupId = "search-service")
    public void handleProductCreated(String message) {
        try {
            log.info("Received product.created event: {}", message);
            ProductEvent event = objectMapper.readValue(message, ProductEvent.class);
            
            ProductDocument document = mapToDocument(event);
            searchService.indexProduct(document);
            
            log.info("Indexed new product: {}", event.getId());
        } catch (Exception e) {
            log.error("Failed to process product.created event", e);
        }
    }

    @KafkaListener(topics = "product.updated", groupId = "search-service")
    public void handleProductUpdated(String message) {
        try {
            log.info("Received product.updated event: {}", message);
            ProductEvent event = objectMapper.readValue(message, ProductEvent.class);
            
            ProductDocument document = mapToDocument(event);
            searchService.indexProduct(document);
            
            log.info("Re-indexed updated product: {}", event.getId());
        } catch (Exception e) {
            log.error("Failed to process product.updated event", e);
        }
    }

    @KafkaListener(topics = "product.deleted", groupId = "search-service")
    public void handleProductDeleted(String message) {
        try {
            log.info("Received product.deleted event: {}", message);
            ProductEvent event = objectMapper.readValue(message, ProductEvent.class);
            
            searchService.deleteProduct(event.getId());
            
            log.info("Deleted product from index: {}", event.getId());
        } catch (Exception e) {
            log.error("Failed to process product.deleted event", e);
        }
    }

    private ProductDocument mapToDocument(ProductEvent event) {
        return ProductDocument.builder()
                .id(event.getId())
                .name(event.getName())
                .description(event.getDescription())
                .sku(event.getSku())
                .category(event.getCategory())
                .price(event.getPrice())
                .currency(event.getCurrency())
                .images(event.getImages())
                .active(event.getActive())
                .stockQuantity(event.getStockQuantity())
                .tags(event.getTags())
                .nameAutocomplete(event.getName())
                .updatedAt(Instant.now())
                .build();
    }
}
