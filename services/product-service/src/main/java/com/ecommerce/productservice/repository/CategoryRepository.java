package com.ecommerce.productservice.repository;

import com.ecommerce.productservice.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Category repository — custom JPQL queries for hierarchical category trees.
 * Hierarchical navigation uses displayOrder for deterministic ordering.
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlug(String slug);

    @Query("SELECT c FROM Category c WHERE c.parent IS NULL ORDER BY c.displayOrder ASC")
    List<Category> findRootCategories();

    @Query("SELECT c FROM Category c WHERE c.parent IS NULL AND c.active = true ORDER BY c.displayOrder ASC")
    List<Category> findActiveRootCategories();

    @Query("SELECT c FROM Category c WHERE c.parent.id = :parentId ORDER BY c.displayOrder ASC")
    List<Category> findByParentId(@Param("parentId") Long parentId);

    @Query("SELECT c FROM Category c WHERE c.parent.id = :parentId AND c.active = true ORDER BY c.displayOrder ASC")
    List<Category> findActiveByParentId(@Param("parentId") Long parentId);

    List<Category> findByParent(Category parent);

    @Query("SELECT c FROM Category c WHERE c.active = true ORDER BY c.displayOrder ASC")
    List<Category> findActiveCategories();

    @Query("SELECT c FROM Category c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Category> searchByName(@Param("name") String name);

    long countByParent(Category parent);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Category c WHERE c.parent.id = :categoryId")
    boolean hasChildren(@Param("categoryId") Long categoryId);

    /**
     * Finds subcategories up to 3 levels deep via explicit parent chain traversal.
     * A recursive CTE would be cleaner but requires DB-specific SQL; this JPQL
     * keeps the code portable across H2 (tests) and MySQL (prod).
     */
    @Query("SELECT c FROM Category c WHERE " +
           "c.parent.id = :categoryId OR " +
           "c.parent.parent.id = :categoryId OR " +
           "c.parent.parent.parent.id = :categoryId")
    List<Category> findAllSubcategories(@Param("categoryId") Long categoryId);
}
