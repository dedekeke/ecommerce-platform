package com.ecommerce.productservice.service;

import com.ecommerce.productservice.model.Category;
import com.ecommerce.productservice.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for Category management with hierarchical support.
 * Optimized for read-heavy workload with caching.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * Get category by ID with caching.
     */
    @Cacheable(value = "categories", key = "#id")
    public Category getCategoryById(Long id) {
        log.debug("Fetching category by ID: {}", id);
        return categoryRepository.findById(id)
            .orElseThrow(() -> new CategoryNotFoundException("Category not found with ID: " + id));
    }

    /**
     * Get category by slug with caching.
     */
    @Cacheable(value = "categories", key = "#slug")
    public Category getCategoryBySlug(String slug) {
        log.debug("Fetching category by slug: {}", slug);
        return categoryRepository.findBySlug(slug)
            .orElseThrow(() -> new CategoryNotFoundException("Category not found with slug: " + slug));
    }

    /**
     * Find category by slug, returning Optional.
     */
    public Optional<Category> findCategoryBySlug(String slug) {
        log.debug("Finding category by slug: {}", slug);
        return categoryRepository.findBySlug(slug);
    }

    /**
     * Get all categories.
     */
    public List<Category> getAllCategories() {
        log.debug("Fetching all categories");
        return categoryRepository.findAll();
    }

    /**
     * Get all active categories.
     */
    public List<Category> getActiveCategories() {
        log.debug("Fetching all active categories");
        return categoryRepository.findActiveCategories();
    }

    /**
     * Get all root categories (categories without a parent).
     */
    public List<Category> getRootCategories() {
        log.debug("Fetching root categories");
        return categoryRepository.findRootCategories();
    }

    /**
     * Get all active root categories.
     */
    public List<Category> getActiveRootCategories() {
        log.debug("Fetching active root categories");
        return categoryRepository.findActiveRootCategories();
    }

    /**
     * Get children of a category.
     */
    public List<Category> getCategoryChildren(Long parentId) {
        log.debug("Fetching children for category ID: {}", parentId);
        return categoryRepository.findByParentId(parentId);
    }

    /**
     * Get active children of a category.
     */
    public List<Category> getActiveCategoryChildren(Long parentId) {
        log.debug("Fetching active children for category ID: {}", parentId);
        return categoryRepository.findActiveByParentId(parentId);
    }

    /**
     * Get all subcategories recursively.
     */
    public List<Category> getAllSubcategories(Long categoryId) {
        log.debug("Fetching all subcategories for category ID: {}", categoryId);
        return categoryRepository.findAllSubcategories(categoryId);
    }

    /**
     * Search categories by name.
     */
    public List<Category> searchCategories(String name) {
        log.debug("Searching categories by name: {}", name);
        return categoryRepository.searchByName(name);
    }

    /**
     * Create a new category.
     */
    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public Category createCategory(Category category) {
        log.info("Creating new category: {}", category.getName());

        // Validate slug uniqueness
        if (categoryRepository.findBySlug(category.getSlug()).isPresent()) {
            throw new CategoryAlreadyExistsException("Category with slug " + category.getSlug() + " already exists");
        }

        // Validate parent if provided
        if (category.getParent() != null && category.getParent().getId() != null) {
            Category parent = categoryRepository.findById(category.getParent().getId())
                .orElseThrow(() -> new CategoryNotFoundException("Parent category not found"));
            category.setParent(parent);
        }

        return categoryRepository.save(category);
    }

    /**
     * Update an existing category.
     */
    @Transactional
    @CacheEvict(value = "categories", key = "#id")
    public Category updateCategory(Long id, Category categoryDetails) {
        log.info("Updating category with ID: {}", id);

        Category category = getCategoryById(id);

        // Update fields
        category.setName(categoryDetails.getName());
        category.setDescription(categoryDetails.getDescription());
        category.setImageUrl(categoryDetails.getImageUrl());
        category.setActive(categoryDetails.getActive());
        category.setDisplayOrder(categoryDetails.getDisplayOrder());

        // Update slug if changed
        if (!category.getSlug().equals(categoryDetails.getSlug())) {
            // Validate new slug uniqueness
            if (categoryRepository.findBySlug(categoryDetails.getSlug()).isPresent()) {
                throw new CategoryAlreadyExistsException("Category with slug " + categoryDetails.getSlug() + " already exists");
            }
            category.setSlug(categoryDetails.getSlug());
        }

        // Update parent if changed
        if (categoryDetails.getParent() != null) {
            if (categoryDetails.getParent().getId() != null) {
                // Prevent circular reference
                if (categoryDetails.getParent().getId().equals(id)) {
                    throw new IllegalArgumentException("A category cannot be its own parent");
                }

                Category newParent = categoryRepository.findById(categoryDetails.getParent().getId())
                    .orElseThrow(() -> new CategoryNotFoundException("Parent category not found"));

                // Check if new parent is not a child of this category (prevent circular hierarchy)
                if (isDescendant(newParent, category)) {
                    throw new IllegalArgumentException("Cannot set a descendant as parent");
                }

                category.setParent(newParent);
            }
        } else {
            category.setParent(null);
        }

        return categoryRepository.save(category);
    }

    /**
     * Move a category to a new parent.
     */
    @Transactional
    @CacheEvict(value = "categories", key = "#categoryId")
    public Category moveCategory(Long categoryId, Long newParentId) {
        log.info("Moving category {} to new parent {}", categoryId, newParentId);

        Category category = getCategoryById(categoryId);

        if (newParentId == null) {
            // Move to root level
            category.setParent(null);
        } else {
            // Prevent moving to itself
            if (categoryId.equals(newParentId)) {
                throw new IllegalArgumentException("A category cannot be its own parent");
            }

            Category newParent = getCategoryById(newParentId);

            // Check if new parent is not a child of this category
            if (isDescendant(newParent, category)) {
                throw new IllegalArgumentException("Cannot move a category to its own descendant");
            }

            category.setParent(newParent);
        }

        return categoryRepository.save(category);
    }

    /**
     * Delete a category (soft delete by marking as inactive).
     * Note: This also marks all children as inactive.
     */
    @Transactional
    @CacheEvict(value = "categories", key = "#id")
    public void deleteCategory(Long id) {
        log.info("Deleting category with ID: {}", id);

        Category category = getCategoryById(id);

        // Check if category has children
        if (categoryRepository.hasChildren(id)) {
            log.warn("Category {} has children. Marking all as inactive.", id);
            // Recursively mark children as inactive
            markChildrenInactive(category);
        }

        category.setActive(false);
        categoryRepository.save(category);
    }

    /**
     * Hard delete a category (permanent deletion).
     * This will also delete all children due to cascade settings.
     */
    @Transactional
    @CacheEvict(value = "categories", key = "#id")
    public void hardDeleteCategory(Long id) {
        log.warn("Hard deleting category with ID: {}", id);

        // Check if category has children
        if (categoryRepository.hasChildren(id)) {
            throw new IllegalStateException("Cannot hard delete a category with children. Delete children first.");
        }

        categoryRepository.deleteById(id);
    }

    /**
     * Check if a category is a descendant of another category.
     * Used to prevent circular hierarchies.
     */
    private boolean isDescendant(Category potentialDescendant, Category ancestor) {
        Category current = potentialDescendant.getParent();
        while (current != null) {
            if (current.getId().equals(ancestor.getId())) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Recursively mark all children of a category as inactive.
     */
    private void markChildrenInactive(Category category) {
        List<Category> children = categoryRepository.findByParent(category);
        for (Category child : children) {
            child.setActive(false);
            categoryRepository.save(child);
            if (categoryRepository.hasChildren(child.getId())) {
                markChildrenInactive(child);
            }
        }
    }

    // Custom exceptions
    public static class CategoryNotFoundException extends RuntimeException {
        public CategoryNotFoundException(String message) {
            super(message);
        }
    }

    public static class CategoryAlreadyExistsException extends RuntimeException {
        public CategoryAlreadyExistsException(String message) {
            super(message);
        }
    }
}
