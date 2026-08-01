package com.ecommerce.cartservice.repository;

import com.ecommerce.cartservice.domain.Cart;
import com.ecommerce.cartservice.domain.CartItem;
import com.ecommerce.cartservice.domain.CartStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the high-traffic cart repository methods do not trigger an
 * N+1 query when the caller iterates {@link Cart#getItems()}. The default
 * lazy {@code @OneToMany} mapping causes one extra query per parent row;
 * the {@code @EntityGraph(attributePaths = "items")} hint flips the load
 * to a single LEFT JOIN, capping query count at 1 for the parents and
 * (depending on Hibernate's plan) 0 or 1 additional batch fetch — but
 * crucially NOT one per parent.
 *
 * <p>We use Hibernate's {@link Statistics} to count actual JDBC prepared
 * statements rather than rely on logs. The threshold {@code <= 2} is chosen
 * because Hibernate may emit a single batch SELECT for items (entity-graph
 * with LEFT JOIN) plus the original cart query. Any value &gt; 2 means we
 * regressed back to N+1.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CartRepositoryNPlusOneTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cartdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled",      () -> "true");
        registry.add("spring.flyway.locations",    () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @Autowired
    private CartRepository cartRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Statistics stats() {
        Session session = entityManager.unwrap(Session.class);
        Statistics s = session.getSessionFactory().getStatistics();
        s.setStatisticsEnabled(true);
        return s;
    }

    @BeforeEach
    void resetStats() {
        stats().clear();
    }

    /**
     * Seeds N carts each with K items and flushes + clears the persistence
     * context so subsequent reads have to hit the DB.
     */
    private List<Cart> seedCarts(int cartCount, int itemsPerCart) {
        Instant idle = Instant.now().minusSeconds(60 * 60 * 24 * 3);
        Instant created = Instant.now().minusSeconds(60 * 60 * 24 * 4);
        for (int i = 0; i < cartCount; i++) {
            Cart cart = Cart.builder()
                    .userId("user-" + i)
                    .status(CartStatus.ACTIVE)
                    .totalAmount(BigDecimal.ZERO)
                    .totalItems(0)
                    .build();
            cart.setCreatedAt(created);
            cart.setUpdatedAt(idle);
            for (int j = 0; j < itemsPerCart; j++) {
                CartItem item = CartItem.builder()
                        .productId("prod-" + i + "-" + j)
                        .productName("Product " + i + "/" + j)
                        .priceSnapshot(BigDecimal.valueOf(9.99))
                        .quantity(1)
                        .subtotal(BigDecimal.valueOf(9.99))
                        .build();
                cart.addItem(item);
            }
            entityManager.persist(cart);
        }
        entityManager.flush();
        entityManager.clear();
        // Return a snapshot for assertion convenience; tests re-fetch.
        return cartRepository.findAll();
    }

    @Test
    void should_avoidNPlusOne_when_findingCartsEligibleForAbandonmentReminder() {
        // Arrange — 5 carts each with 3 items, all idle and never reminded.
        seedCarts(5, 3);
        entityManager.clear();
        stats().clear();

        // Act — repository returns carts, then we iterate items the way
        // AbandonedCartScanner does.
        List<Cart> carts = cartRepository.findCartsEligibleForAbandonmentReminder(
                CartStatus.ACTIVE,
                Instant.now(),                         // idle threshold = "before now"
                Instant.now().minusSeconds(60));       // reminder cutoff
        carts.forEach(c -> c.getItems().size());       // realistic iteration

        // Assert — without @EntityGraph this would be 1 + 5 = 6 queries.
        // With the entity graph, Hibernate fetches in 1 (LEFT JOIN) or 2
        // (parent + batched items) — we cap at 2.
        long queries = stats().getPrepareStatementCount();
        assertThat(carts).hasSize(5);
        assertThat(queries)
                .as("findCartsEligibleForAbandonmentReminder must not trigger N+1; got %s queries", queries)
                .isLessThanOrEqualTo(2);
    }

    @Test
    void should_avoidNPlusOne_when_findingCartByUserIdAndStatus() {
        // Arrange — single cart with 3 items.
        seedCarts(1, 3);
        entityManager.clear();
        stats().clear();

        // Act
        Cart cart = cartRepository.findByUserIdAndStatus("user-0", CartStatus.ACTIVE).orElseThrow();
        int itemCount = cart.getItems().size();

        // Assert — single fetch should pull cart + items in <= 2 queries.
        long queries = stats().getPrepareStatementCount();
        assertThat(itemCount).isEqualTo(3);
        assertThat(queries)
                .as("findByUserIdAndStatus must not trigger N+1; got %s queries", queries)
                .isLessThanOrEqualTo(2);
    }

    @Test
    void should_avoidNPlusOne_when_findingExpiredCarts() {
        // Arrange — 4 expired carts each with 2 items.
        Instant expiredAt = Instant.now().minusSeconds(60);
        for (int i = 0; i < 4; i++) {
            Cart cart = Cart.builder()
                    .userId("expired-" + i)
                    .status(CartStatus.ACTIVE)
                    .totalAmount(BigDecimal.ZERO)
                    .totalItems(0)
                    .expiresAt(expiredAt)
                    .build();
            cart.setUpdatedAt(Instant.now().minusSeconds(120));
            for (int j = 0; j < 2; j++) {
                CartItem item = CartItem.builder()
                        .productId("p-" + i + "-" + j)
                        .productName("Item " + j)
                        .priceSnapshot(BigDecimal.ONE)
                        .quantity(1)
                        .subtotal(BigDecimal.ONE)
                        .build();
                cart.addItem(item);
            }
            entityManager.persist(cart);
        }
        entityManager.flush();
        entityManager.clear();
        stats().clear();

        // Act
        List<Cart> expired = cartRepository.findExpiredCarts(Instant.now(), CartStatus.ACTIVE);
        expired.forEach(c -> c.getItems().size());

        // Assert
        long queries = stats().getPrepareStatementCount();
        assertThat(expired).hasSize(4);
        assertThat(queries)
                .as("findExpiredCarts must not trigger N+1; got %s queries", queries)
                .isLessThanOrEqualTo(2);
    }
}
