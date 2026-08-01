package com.ecommerce.productservice.repository;

import com.ecommerce.productservice.config.CacheConfig;
import com.ecommerce.productservice.dto.CachedProductPage;
import com.ecommerce.productservice.dto.ProductResponse;
import com.ecommerce.productservice.mapper.CategoryMapper;
import com.ecommerce.productservice.mapper.ProductMapper;
import com.ecommerce.productservice.model.Category;
import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.service.ProductListingCache;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Docker-free proof that a cached product listing survives the <em>real</em> L2
 * (Redis) serialization path even though the source products carry an EAGER
 * {@code images} {@code PersistentSet} and an unfetched {@code @ManyToOne(LAZY)}
 * category.
 *
 * <p>Reviewer's MEDIUM concern (PR #113): caching entities is unsafe. A first cut
 * that cached {@code Product} entities failed exactly here — the polymorphic
 * serializer wrote Hibernate runtime types into {@code @class} and deserialization
 * threw {@code LazyInitializationException} rebuilding a {@code PersistentSet}
 * with no Session. The fix maps to {@link ProductResponse} DTOs inside the
 * session ({@link ProductListingCache}), so the cached value is a plain POJO
 * graph.
 *
 * <p>This test loads through the cache path, then <b>clears the persistence
 * context</b> (worst case: entities detached, no session) before the actual
 * {@link CacheConfig#redisSerializer()} round-trip. It asserts: no exception, no
 * Hibernate proxy artifact in the blob, and the category (including derived
 * fields the mapper computes under the session) is preserved through Redis.
 */
@DataJpaTest
@ContextConfiguration(classes = RepositoryTestConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.default_batch_fetch_size=100",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class ProductListingCacheSerializationTest {

    private static final int PRODUCT_COUNT = 5;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private final GenericJackson2JsonRedisSerializer serializer = new CacheConfig().redisSerializer();

    private ProductListingCache listingCache() {
        return new ProductListingCache(productRepository, new ProductMapper(new CategoryMapper()));
    }

    private Statistics stats() {
        Session session = entityManager.unwrap(Session.class);
        Statistics s = session.getSessionFactory().getStatistics();
        s.setStatisticsEnabled(true);
        return s;
    }

    @BeforeEach
    void seed() {
        Category electronics = categoryRepository.save(Category.builder()
                .name("Electronics").slug("electronics").active(true).build());
        for (int i = 0; i < PRODUCT_COUNT; i++) {
            entityManager.persist(Product.builder()
                    .sku("SKU-" + i)
                    .name("Product " + i)
                    .price(new BigDecimal("19.99"))
                    .currency("USD")
                    .stockQuantity(10)
                    .active(true)
                    .category(electronics)
                    .images(Set.of("https://cdn.example.com/" + i + ".jpg"))
                    .build());
        }
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void should_roundTripThroughRedisSerializer_andKeepCategory_afterSessionClosed() {
        // Load via the cache path (maps to DTOs in-session), then detach everything
        // to emulate serialization after the transaction has committed.
        CachedProductPage snapshot = listingCache().getActiveProducts(PageRequest.of(0, PRODUCT_COUNT));
        entityManager.clear();

        byte[] blob = serializer.serialize(snapshot);
        assertThatCode(() -> serializer.serialize(snapshot)).doesNotThrowAnyException();

        String json = new String(blob, StandardCharsets.UTF_8);
        assertThat(json)
                .as("no Hibernate proxy artifact must leak into the cached blob")
                .doesNotContain("hibernateLazyInitializer");

        CachedProductPage restored = (CachedProductPage) serializer.deserialize(blob);
        assertThat(restored).isNotNull();
        assertThat(restored.getContent()).hasSize(PRODUCT_COUNT);
        assertThat(restored.getTotalElements()).isEqualTo(PRODUCT_COUNT);

        // Category was mapped under the session and survives the round-trip. If a
        // future mapper change stops populating category, this fails loudly.
        ProductResponse restoredProduct = restored.getContent().get(0);
        assertThat(restoredProduct.getImages()).hasSize(1);
        assertThat(restoredProduct.getCategory()).isNotNull();
        assertThat(restoredProduct.getCategory().getName()).isEqualTo("Electronics");
        assertThat(restoredProduct.getCategory().getSlug()).isEqualTo("electronics");
    }

    @Test
    void should_notBlowQueryBudget_when_buildingACachedPage() {
        stats().clear();

        listingCache().getActiveProducts(PageRequest.of(0, PRODUCT_COUNT));

        // count + products + batched images + batched category proxy + batched
        // category.children (mapper#hasChildren) — a small constant, NOT one per
        // product. Batching (default_batch_fetch_size) keeps the associations to
        // one query each regardless of page size.
        long queries = stats().getPrepareStatementCount();
        assertThat(queries)
                .as("associations must be batch-loaded, not one query per product; got %s", queries)
                .isLessThanOrEqualTo(6);
    }
}
