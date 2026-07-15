package com.ecommerce.productservice.repository;

import com.ecommerce.productservice.model.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Docker-free regression guard for the {@code Product.images} N+1.
 *
 * <p>{@code Product.images} is an {@code @ElementCollection(fetch = EAGER)}. When a
 * page of products is loaded, Hibernate must initialise every product's image
 * collection. Without batching that is one extra {@code product_images} SELECT
 * <em>per product</em> — a classic N+1 (a 20-item page ≈ 22 statements: 1 count
 * + 1 products + 20 images).
 *
 * <p>The fix is {@code hibernate.default_batch_fetch_size} (set to 100 in
 * {@code application.yml}, mirrored here via {@link TestPropertySource}), which
 * collapses the collection loads into a single
 * {@code ... WHERE product_id IN (?,?,..)} batch. Expected statements: 1 count +
 * 1 products + 1 batched images = 3.
 *
 * <p>Runs on in-memory H2 (no Testcontainers/Docker) using Hibernate
 * {@link Statistics} to count real JDBC prepared statements — the same counting
 * technique as {@code CartRepositoryNPlusOneTest}, minus the container.
 */
@DataJpaTest
@ContextConfiguration(classes = RepositoryTestConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.default_batch_fetch_size=100",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class ProductImagesBatchFetchTest {

    private static final int PRODUCT_COUNT = 20;
    private static final int IMAGES_PER_PRODUCT = 4;

    @Autowired
    private ProductRepository productRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Statistics stats() {
        Session session = entityManager.unwrap(Session.class);
        Statistics s = session.getSessionFactory().getStatistics();
        s.setStatisticsEnabled(true);
        return s;
    }

    @BeforeEach
    void seed() {
        for (int i = 0; i < PRODUCT_COUNT; i++) {
            Product product = Product.builder()
                    .sku("SKU-" + i)
                    .name("Product " + i)
                    .price(new BigDecimal("19.99"))
                    .currency("USD")
                    .stockQuantity(10)
                    .active(true)
                    .images(Set.of(
                            "https://cdn.example.com/" + i + "/a.jpg",
                            "https://cdn.example.com/" + i + "/b.jpg",
                            "https://cdn.example.com/" + i + "/c.jpg",
                            "https://cdn.example.com/" + i + "/d.jpg"))
                    .build();
            entityManager.persist(product);
        }
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void should_batchFetchImages_when_loadingAPageOfProducts() {
        // Arrange
        stats().clear();

        // Act — load a full page, then touch every product's images the way the
        // response mapper (ProductMapper#toResponse) does.
        Page<Product> page = productRepository.findByActiveTrue(PageRequest.of(0, PRODUCT_COUNT));
        page.getContent().forEach(p -> p.getImages().size());

        // Assert — batch fetching caps this at 3 (count + products + 1 batched
        // images SELECT). Regressing to per-row image loads would be ~22.
        long queries = stats().getPrepareStatementCount();
        assertThat(page.getContent()).hasSize(PRODUCT_COUNT);
        assertThat(page.getContent().get(0).getImages()).hasSize(IMAGES_PER_PRODUCT);
        assertThat(queries)
                .as("EAGER Product.images must be batch-fetched, not one query per product; got %s", queries)
                .isLessThanOrEqualTo(3);
    }
}
