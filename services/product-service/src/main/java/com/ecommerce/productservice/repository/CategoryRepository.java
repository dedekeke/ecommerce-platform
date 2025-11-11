package com.ecommerce.productservice.repository;

import com.ecommerce.productservice.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Category entity with support for hierarchical queries.
 *
 * Provides custom queries for:
 * - Finding root categories (no parent)
 * - Finding children of a category
 * - Finding by slug
 * - Active category filtering
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Find category by slug.
     */
    Optional<Category> findBySlug(String slug);

    /**
     * Find all root categories (categories without a parent).
     * Ordered by display order.
     */
    @Query("SELECT c FROM Category c WHERE c.parent IS NULL ORDER BY c.displayOrder ASC")
    List<Category> findRootCategories();

    /**
     * Find all active root categories.
     */
    @Query("SELECT c FROM Category c WHERE c.parent IS NULL AND c.active = true ORDER BY c.displayOrder ASC")
    List<Category> findActiveRootCategories();

    /**
     * Find children of a specific category.
     * Ordered by display order.
     */
    @Query("SELECT c FROM Category c WHERE c.parent.id = :parentId ORDER BY c.displayOrder ASC")
    List<Category> findByParentId(@Param("parentId") Long parentId);

    /**
     * Find active children of a specific category.
     */
    @Query("SELECT c FROM Category c WHERE c.parent.id = :parentId AND c.active = true ORDER BY c.displayOrder ASC")
    List<Category> findActiveByParentId(@Param("parentId") Long parentId);

    /**
     * Find all categories by parent.
     */
    List<Category> findByParent(Category parent);

    /**
     * Find all active categories.
     * Ordered by display order.
     */
    @Query("SELECT c FROM Category c WHERE c.active = true ORDER BY c.displayOrder ASC")
    List<Category> findActiveCategories();

    /**
     * Find categories by name (case-insensitive search).
     */
    @Query("SELECT c FROM Category c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Category> searchByName(@Param("name") String name);

    /**
     * Count children of a category.
     */
    long countByParent(Category parent);

    /**
     * Check if a category has children.
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Category c WHERE c.parent.id = :categoryId")
    boolean hasChildren(@Param("categoryId") Long categoryId);

    /**
     * Find all subcategories recursively (up to 3 levels deep).
     * This is useful for getting all products in a category hierarchy.
     */
    @Query("SELECT c FROM Category c WHERE " +
           "c.parent.id = :categoryId OR " +
           "c.parent.parent.id = :categoryId OR " +
           "c.parent.parent.parent.id = :categoryId")
    List<Category> findAllSubcategories(@Param("categoryId") Long categoryId);
}
