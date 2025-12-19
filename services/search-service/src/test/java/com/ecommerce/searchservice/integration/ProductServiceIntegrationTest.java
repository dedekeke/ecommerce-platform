package com.ecommerce.searchservice.integration;

import com.ecommerce.searchservice.document.ProductDocument;
import com.ecommerce.searchservice.event.ProductEvent;
import com.ecommerce.searchservice.repository.ProductSearchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Product Service synchronization with Search Service via Kafka.
 */
@SpringBootTest
@Testcontainers
class ProductServiceIntegrationTest {

    @Container
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:8.11.0"
    )
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0")
    );

    @Autowired
    private ProductSearchRepository productSearchRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", elasticsearch::getHttpHostAddress);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @BeforeEach
    void setUp() {
        productSearchRepository.deleteAll();
    }

    @Test
    void shouldIndexProductWhenProductCreatedEventReceived() throws Exception {
        // Given
        ProductEvent productEvent = ProductEvent.builder()
                .id("test-product-1")
                .name("Test Laptop")
                .description("High-performance gaming laptop")
                .sku("LAP-001")
                .category("electronics")
                .price(new BigDecimal("1299.99"))
                .currency("USD")
                .images(List.of("image1.jpg", "image2.jpg"))
                .active(true)
                .stockQuantity(50)
                .tags(List.of("gaming", "laptop", "portable"))
                .eventType("CREATED")
                .build();

        String eventJson = objectMapper.writeValueAsString(productEvent);

        // When - Send Kafka event
        kafkaTemplate.send("product.created", productEvent.getId(), eventJson).get();

        // Then - Verify product is indexed in Elasticsearch
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    Optional<ProductDocument> indexed = productSearchRepository.findById("test-product-1");
                    assertThat(indexed).isPresent();
                    assertThat(indexed.get().getName()).isEqualTo("Test Laptop");
                    assertThat(indexed.get().getCategory()).isEqualTo("electronics");
                    assertThat(indexed.get().getPrice()).isEqualByComparingTo(new BigDecimal("1299.99"));
                });
    }

    @Test
    void shouldUpdateProductWhenProductUpdatedEventReceived() throws Exception {
        // Given - Create initial product
        ProductDocument initialProduct = new ProductDocument();
        initialProduct.setId("test-product-2");
        initialProduct.setName("Test Phone");
        initialProduct.setDescription("Smartphone");
        initialProduct.setSku("PHN-001");
        initialProduct.setCategory("electronics");
        initialProduct.setPrice(new BigDecimal("799.99"));
        initialProduct.setCurrency("USD");
        initialProduct.setActive(true);
        initialProduct.setStockQuantity(100);
        productSearchRepository.save(initialProduct);

        // When - Send update event
        ProductEvent updateEvent = ProductEvent.builder()
                .id("test-product-2")
                .name("Updated Phone")
                .description("Latest smartphone model")
                .sku("PHN-001")
                .category("electronics")
                .price(new BigDecimal("899.99"))
                .currency("USD")
                .images(List.of("phone1.jpg"))
                .active(true)
                .stockQuantity(80)
                .tags(List.of("smartphone", "5G"))
                .eventType("UPDATED")
                .build();

        String eventJson = objectMapper.writeValueAsString(updateEvent);
        kafkaTemplate.send("product.updated", updateEvent.getId(), eventJson).get();

        // Then - Verify product is updated
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    Optional<ProductDocument> updated = productSearchRepository.findById("test-product-2");
                    assertThat(updated).isPresent();
                    assertThat(updated.get().getName()).isEqualTo("Updated Phone");
                    assertThat(updated.get().getDescription()).isEqualTo("Latest smartphone model");
                    assertThat(updated.get().getPrice()).isEqualByComparingTo(new BigDecimal("899.99"));
                    assertThat(updated.get().getStockQuantity()).isEqualTo(80);
                });
    }

    @Test
    void shouldDeleteProductWhenProductDeletedEventReceived() throws Exception {
        // Given - Create product
        ProductDocument product = new ProductDocument();
        product.setId("test-product-3");
        product.setName("Test Tablet");
        product.setDescription("Android tablet");
        product.setSku("TAB-001");
        product.setCategory("electronics");
        product.setPrice(new BigDecimal("399.99"));
        product.setCurrency("USD");
        product.setActive(true);
        product.setStockQuantity(25);
        productSearchRepository.save(product);

        // Verify product exists
        assertThat(productSearchRepository.findById("test-product-3")).isPresent();

        // When - Send delete event
        ProductEvent deleteEvent = ProductEvent.builder()
                .id("test-product-3")
                .eventType("DELETED")
                .build();

        String eventJson = objectMapper.writeValueAsString(deleteEvent);
        kafkaTemplate.send("product.deleted", deleteEvent.getId(), eventJson).get();

        // Then - Verify product is deleted
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    Optional<ProductDocument> deleted = productSearchRepository.findById("test-product-3");
                    assertThat(deleted).isEmpty();
                });
    }

    @Test
    void shouldHandleMultipleProductEventsInSequence() throws Exception {
        // Given
        List<ProductEvent> events = List.of(
                ProductEvent.builder()
                        .id("prod-1")
                        .name("Product 1")
                        .description("Description 1")
                        .sku("SKU-001")
                        .category("books")
                        .price(new BigDecimal("19.99"))
                        .currency("USD")
                        .active(true)
                        .stockQuantity(100)
                        .eventType("CREATED")
                        .build(),
                ProductEvent.builder()
                        .id("prod-2")
                        .name("Product 2")
                        .description("Description 2")
                        .sku("SKU-002")
                        .category("electronics")
                        .price(new BigDecimal("299.99"))
                        .currency("USD")
                        .active(true)
                        .stockQuantity(50)
                        .eventType("CREATED")
                        .build(),
                ProductEvent.builder()
                        .id("prod-3")
                        .name("Product 3")
                        .description("Description 3")
                        .sku("SKU-003")
                        .category("clothing")
                        .price(new BigDecimal("49.99"))
                        .currency("USD")
                        .active(true)
                        .stockQuantity(200)
                        .eventType("CREATED")
                        .build()
        );

        // When - Send all events
        for (ProductEvent event : events) {
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("product.created", event.getId(), eventJson).get();
        }

        // Then - Verify all products are indexed
        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    long count = productSearchRepository.count();
                    assertThat(count).isEqualTo(3);

                    assertThat(productSearchRepository.findById("prod-1")).isPresent();
                    assertThat(productSearchRepository.findById("prod-2")).isPresent();
                    assertThat(productSearchRepository.findById("prod-3")).isPresent();
                });
    }
}
