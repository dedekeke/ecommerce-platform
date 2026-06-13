package com.ecommerce.productservice.repository;

import com.ecommerce.productservice.model.Category;
import com.ecommerce.productservice.model.Dimensions;
import com.ecommerce.productservice.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ProductRepository using Testcontainers.
 */
@DataJpaTest
@ContextConfiguration(classes = RepositoryTestConfig.class)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryTest {

    @Container
    static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.2")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category electronics;
    private Category computers;
    private Product laptop;
    private Product phone;

    @BeforeEach
    void setUp() {
        // Clear all data
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        // Create test categories
        electronics = Category.builder()
            .name("Electronics")
            .slug("electronics")
            .active(true)
            .displayOrder(1)
            .build();
        electronics = categoryRepository.save(electronics);

        computers = Category.builder()
            .name("Computers")
            .slug("computers")
            .parent(electronics)
            .active(true)
            .displayOrder(1)
            .build();
        computers = categoryRepository.save(computers);

        // Create test products
        laptop = Product.builder()
            .sku("LAPTOP-001")
            .name("Dell XPS 13")
            .description("High-performance ultrabook")
            .category(computers)
            .price(new BigDecimal("1299.99"))
            .currency("USD")
            .stockQuantity(15)
            .active(true)
            .dimensions(Dimensions.builder()
                .length(new BigDecimal("30.0"))
                .width(new BigDecimal("20.0"))
                .height(new BigDecimal("1.5"))
                .weight(new BigDecimal("1.2"))
                .build())
            .build();
        laptop = productRepository.save(laptop);

        phone = Product.builder()
            .sku("PHONE-001")
            .name("iPhone 15 Pro")
            .description("Latest iPhone with A17 Pro chip")
            .category(electronics)
            .price(new BigDecimal("999.99"))
            .currency("USD")
            .stockQuantity(25)
            .active(true)
            .build();
        phone = productRepository.save(phone);
    }

    @Test
    void testFindBySku() {
        Optional<Product> found = productRepository.findBySku("LAPTOP-001");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Dell XPS 13");
    }

    @Test
    void testSearchByNameOrDescription() {
        Pageable pageable = PageRequest.of(0, 10);

        Page<Product> results = productRepository.searchByNameOrDescription("ultrabook", pageable);

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getSku()).isEqualTo("LAPTOP-001");
    }

    @Test
    void testFindByCategory() {
        Pageable pageable = PageRequest.of(0, 10);

        Page<Product> results = productRepository.findByCategory(computers, pageable);

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getSku()).isEqualTo("LAPTOP-001");
    }

    @Test
    void testFindByCategoryOrSubcategories() {
        Pageable pageable = PageRequest.of(0, 10);

        // Search in parent category should find products in subcategories too
        Page<Product> results = productRepository.findByCategoryOrSubcategories(electronics, pageable);

        assertThat(results.getContent()).hasSize(2); // Both laptop and phone
    }

    @Test
    void testFindByPriceBetween() {
        Pageable pageable = PageRequest.of(0, 10);

        Page<Product> results = productRepository.findByPriceBetween(
            new BigDecimal("900"),
            new BigDecimal("1100"),
            pageable
        );

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getSku()).isEqualTo("PHONE-001");
    }

    @Test
    void testFindInStock() {
        Pageable pageable = PageRequest.of(0, 10);

        Page<Product> results = productRepository.findInStock(pageable);

        assertThat(results.getContent()).hasSize(2);
    }

    @Test
    void testFindAvailable() {
        // Mark one product as inactive
        laptop.setActive(false);
        productRepository.save(laptop);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> results = productRepository.findAvailable(pageable);

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getSku()).isEqualTo("PHONE-001");
    }

    @Test
    void testAdvancedSearch() {
        Pageable pageable = PageRequest.of(0, 10);

        Page<Product> results = productRepository.advancedSearch(
            "iPhone",
            null,
            new BigDecimal("900"),
            new BigDecimal("1500"),
            true,
            true,
            pageable
        );

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getSku()).isEqualTo("PHONE-001");
    }

    @Test
    void testFindLowStockProducts() {
        // Set laptop to low stock
        laptop.setStockQuantity(5);
        productRepository.save(laptop);

        List<Product> results = productRepository.findLowStockProducts(10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSku()).isEqualTo("LAPTOP-001");
    }

    @Test
    void testCountByCategory() {
        long count = productRepository.countByCategory(computers);

        assertThat(count).isEqualTo(1);
    }
}
