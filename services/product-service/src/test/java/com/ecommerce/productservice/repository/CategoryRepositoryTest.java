package com.ecommerce.productservice.repository;

import com.ecommerce.productservice.model.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CategoryRepository using Testcontainers.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CategoryRepositoryTest {

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
    private CategoryRepository categoryRepository;

    private Category electronics;
    private Category computers;
    private Category laptops;
    private Category clothing;

    @BeforeEach
    void setUp() {
        // Clear all data
        categoryRepository.deleteAll();

        // Create test hierarchy:
        // Electronics -> Computers -> Laptops
        // Clothing (root)
        electronics = Category.builder()
            .name("Electronics")
            .slug("electronics")
            .description("Electronic devices")
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

        laptops = Category.builder()
            .name("Laptops")
            .slug("laptops")
            .parent(computers)
            .active(true)
            .displayOrder(1)
            .build();
        laptops = categoryRepository.save(laptops);

        clothing = Category.builder()
            .name("Clothing")
            .slug("clothing")
            .description("Fashion and apparel")
            .active(false) // Inactive
            .displayOrder(2)
            .build();
        clothing = categoryRepository.save(clothing);
    }

    @Test
    void testFindBySlug() {
        Optional<Category> found = categoryRepository.findBySlug("electronics");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Electronics");
    }

    @Test
    void testFindRootCategories() {
        List<Category> roots = categoryRepository.findRootCategories();

        assertThat(roots).hasSize(2); // Electronics and Clothing
        assertThat(roots).extracting(Category::getName)
            .containsExactlyInAnyOrder("Electronics", "Clothing");
    }

    @Test
    void testFindActiveRootCategories() {
        List<Category> activeRoots = categoryRepository.findActiveRootCategories();

        assertThat(activeRoots).hasSize(1);
        assertThat(activeRoots.get(0).getName()).isEqualTo("Electronics");
    }

    @Test
    void testFindByParentId() {
        List<Category> children = categoryRepository.findByParentId(electronics.getId());

        assertThat(children).hasSize(1);
        assertThat(children.get(0).getName()).isEqualTo("Computers");
    }

    @Test
    void testFindActiveByParentId() {
        List<Category> activeChildren = categoryRepository.findActiveByParentId(electronics.getId());

        assertThat(activeChildren).hasSize(1);
        assertThat(activeChildren.get(0).getName()).isEqualTo("Computers");
    }

    @Test
    void testFindByParent() {
        List<Category> children = categoryRepository.findByParent(electronics);

        assertThat(children).hasSize(1);
        assertThat(children.get(0).getName()).isEqualTo("Computers");
    }

    @Test
    void testFindActiveCategories() {
        List<Category> activeCategories = categoryRepository.findActiveCategories();

        assertThat(activeCategories).hasSize(3); // Electronics, Computers, Laptops
        assertThat(activeCategories).extracting(Category::getName)
            .containsExactlyInAnyOrder("Electronics", "Computers", "Laptops");
    }

    @Test
    void testSearchByName() {
        List<Category> results = categoryRepository.searchByName("comput");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Computers");
    }

    @Test
    void testCountByParent() {
        long count = categoryRepository.countByParent(electronics);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void testHasChildren() {
        boolean hasChildren = categoryRepository.hasChildren(electronics.getId());
        boolean hasNoChildren = categoryRepository.hasChildren(laptops.getId());

        assertThat(hasChildren).isTrue();
        assertThat(hasNoChildren).isFalse();
    }

    @Test
    void testFindAllSubcategories() {
        List<Category> subcategories = categoryRepository.findAllSubcategories(electronics.getId());

        assertThat(subcategories).hasSize(2); // Computers and Laptops
        assertThat(subcategories).extracting(Category::getName)
            .containsExactlyInAnyOrder("Computers", "Laptops");
    }

    @Test
    void testCategoryHierarchyMethods() {
        // Test isRootCategory()
        assertThat(electronics.isRootCategory()).isTrue();
        assertThat(computers.isRootCategory()).isFalse();

        // Test hasChildren()
        assertThat(electronics.hasChildren()).isTrue();
        assertThat(clothing.hasChildren()).isFalse();

        // Test getLevel()
        assertThat(electronics.getLevel()).isEqualTo(0); // Root
        assertThat(computers.getLevel()).isEqualTo(1); // 1 level deep
        assertThat(laptops.getLevel()).isEqualTo(2); // 2 levels deep

        // Test getFullPath()
        assertThat(laptops.getFullPath()).isEqualTo("Electronics / Computers / Laptops");
    }
}
