package com.ecommerce.searchservice.admin;

import com.ecommerce.searchservice.document.ProductDocument;
import com.ecommerce.searchservice.repository.ProductSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link BrandRatingBackfillService} against a real
 * Elasticsearch (Testcontainers). Verifies that the {@code _update_by_query}
 * populates {@code brand}/{@code rating} on legacy documents, is idempotent on
 * re-run, and never overwrites values that are already present.
 */
@SpringBootTest
@Testcontainers
@DisplayName("BrandRatingBackfillService (Testcontainers ES)")
class BrandRatingBackfillServiceIntegrationTest {

    @Container
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:8.11.0")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", elasticsearch::getHttpHostAddress);
        // The kafka consumers are not exercised here; point them at a dummy broker.
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:59092");
        registry.add("spring.kafka.consumer.auto-startup", () -> "false");
        // Disable the embedded gRPC server (net.devh starter) so two cached
        // @SpringBootTest contexts don't fight over the default port 9090.
        registry.add("grpc.server.port", () -> "-1");
        registry.add("eureka.client.enabled", () -> "false");
    }

    @Autowired
    private ProductSearchRepository repository;

    @Autowired
    private BrandRatingBackfillService backfillService;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("should_deriveBrandFromSku_andDefaultRating_when_fieldsMissing")
    void should_deriveBrandFromSku_andDefaultRating_when_fieldsMissing() throws Exception {
        repository.save(legacyDoc("doc-1", "ACME-1001"));
        repository.save(legacyDoc("doc-2", "SONY-2002"));

        long updated = backfillService.backfill();

        assertThat(updated).isEqualTo(2);
        ProductDocument d1 = repository.findById("doc-1").orElseThrow();
        ProductDocument d2 = repository.findById("doc-2").orElseThrow();
        assertThat(d1.getBrand()).isEqualTo("ACME");
        assertThat(d1.getRating()).isEqualTo(0.0);
        assertThat(d2.getBrand()).isEqualTo("SONY");
    }

    @Test
    @DisplayName("should_beIdempotent_when_runTwice")
    void should_beIdempotent_when_runTwice() throws Exception {
        repository.save(legacyDoc("doc-1", "ACME-1001"));

        long first = backfillService.backfill();
        long second = backfillService.backfill();

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
    }

    @Test
    @DisplayName("should_preserveExistingValues_when_alreadyPopulated")
    void should_preserveExistingValues_when_alreadyPopulated() throws Exception {
        ProductDocument populated = legacyDoc("doc-3", "DELL-3003");
        populated.setBrand("CUSTOM_BRAND");
        populated.setRating(4.5);
        repository.save(populated);
        // a sibling doc that does need backfill, to ensure the query still runs
        repository.save(legacyDoc("doc-4", "HP-4004"));

        backfillService.backfill();

        ProductDocument unchanged = repository.findById("doc-3").orElseThrow();
        assertThat(unchanged.getBrand()).isEqualTo("CUSTOM_BRAND");
        assertThat(unchanged.getRating()).isEqualTo(4.5);
        assertThat(repository.findById("doc-4").orElseThrow().getBrand()).isEqualTo("HP");
    }

    private static ProductDocument legacyDoc(String id, String sku) {
        return ProductDocument.builder()
                .id(id)
                .name("Legacy " + id)
                .description("indexed before brand/rating existed")
                .sku(sku)
                .category("electronics")
                .price(new BigDecimal("99.99"))
                .currency("USD")
                .active(true)
                .stockQuantity(10)
                .build();
    }
}
