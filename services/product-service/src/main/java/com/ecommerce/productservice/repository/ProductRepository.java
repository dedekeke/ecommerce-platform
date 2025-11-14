package com.ecommerce.productservice.repository;

import com.ecommerce.productservice.model.Category;
import com.ecommerce.productservice.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Product entity with optimized queries for read-heavy workload.
 *
 * Provides custom queries for:
 * - Full-text search by name/description
 * - Filtering by category (including subcategories)
 * - Price range filtering
 * - Pagination support
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Find product by SKU.
     */
    Optional<Product> findBySku(String sku);

    /**
     * Search products by name or description (case-insensitive).
     * Optimized for read-heavy workload with proper indexing.
     */
    @Query("SELECT p FROM Product p WHERE " +
           "LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Product> searchByNameOrDescription(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Find products by category.
     */
    Page<Product> findByCategory(Category category, Pageable pageable);

    /**
     * Find products by category ID.
     */
    @Query("SELECT p FROM Product p WHERE p.category.id = :categoryId")
    Page<Product> findByCategoryId(@Param("categoryId") Long categoryId, Pageable pageable);

    /**
     * Find products by category or any of its subcategories.
     * This is useful for hierarchical category filtering.
     */
    @Query("SELECT p FROM Product p WHERE " +
           "p.category = :category OR " +
           "p.category.parent = :category OR " +
           "p.category.parent.parent = :category")
    Page<Product> findByCategoryOrSubcategories(@Param("category") Category category, Pageable pageable);

    /**
     * Find products within a price range.
     */
    @Query("SELECT p FROM Product p WHERE p.price BETWEEN :minPrice AND :maxPrice")
    Page<Product> findByPriceBetween(@Param("minPrice") BigDecimal minPrice,
                                      @Param("maxPrice") BigDecimal maxPrice,
                                      Pageable pageable);

    /**
     * Find active products only.
     */
    Page<Product> findByActiveTrue(Pageable pageable);

    /**
     * Find products in stock (stockQuantity > 0).
     */
    @Query("SELECT p FROM Product p WHERE p.stockQuantity > 0")
    Page<Product> findInStock(Pageable pageable);

    /**
     * Find available products (active and in stock).
     */
    @Query("SELECT p FROM Product p WHERE p.active = true AND p.stockQuantity > 0")
    Page<Product> findAvailable(Pageable pageable);

    /**
     * Advanced search with multiple filters.
     * All parameters are optional.
     */
    @Query("SELECT p FROM Product p WHERE " +
           "(:searchTerm IS NULL OR " +
           "  LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "  LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) AND " +
           "(:categoryId IS NULL OR p.category.id = :categoryId) AND " +
           "(:minPrice IS NULL OR p.price >= :minPrice) AND " +
           "(:maxPrice IS NULL OR p.price <= :maxPrice) AND " +
           "(:activeOnly = false OR p.active = true) AND " +
           "(:inStockOnly = false OR p.stockQuantity > 0)")
    Page<Product> advancedSearch(@Param("searchTerm") String searchTerm,
                                  @Param("categoryId") Long categoryId,
                                  @Param("minPrice") BigDecimal minPrice,
                                  @Param("maxPrice") BigDecimal maxPrice,
                                  @Param("activeOnly") boolean activeOnly,
                                  @Param("inStockOnly") boolean inStockOnly,
                                  Pageable pageable);

    /**
     * Count products by category.
     */
    long countByCategory(Category category);

    /**
     * Find products with low stock (below threshold).
     */
    @Query("SELECT p FROM Product p WHERE p.stockQuantity <= :threshold AND p.active = true")
    List<Product> findLowStockProducts(@Param("threshold") int threshold);

    /**
     * Find featured products (you can customize this based on your business logic).
     * For now, it returns products sorted by stock quantity descending.
     */
    @Query("SELECT p FROM Product p WHERE p.active = true ORDER BY p.stockQuantity DESC")
    Page<Product> findFeaturedProducts(Pageable pageable);
}
