package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.dto.CategoryRequest;
import com.ecommerce.productservice.dto.CategoryResponse;
import com.ecommerce.productservice.mapper.CategoryMapper;
import com.ecommerce.productservice.model.Category;
import com.ecommerce.productservice.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for Category management.
 */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Categories", description = "Category hierarchy management")
public class CategoryController {

    private final CategoryService categoryService;
    private final CategoryMapper categoryMapper;

    @GetMapping
    @Operation(summary = "Get all categories")
    public ResponseEntity<List<CategoryResponse>> getAllCategories(
            @Parameter(description = "Include only active categories")
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly
    ) {
        log.info("GET /api/v1/categories - activeOnly: {}", activeOnly);

        List<Category> categories = activeOnly ?
            categoryService.getActiveCategories() :
            categoryService.getAllCategories();

        List<CategoryResponse> response = categories.stream()
            .map(categoryMapper::toResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/root")
    @Operation(summary = "Get root categories (categories without a parent)")
    public ResponseEntity<List<CategoryResponse>> getRootCategories(
            @Parameter(description = "Include only active categories")
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly,
            @Parameter(description = "Include children in response")
            @RequestParam(required = false, defaultValue = "true") boolean includeChildren
    ) {
        log.info("GET /api/v1/categories/root - activeOnly: {}, includeChildren: {}", activeOnly, includeChildren);

        List<Category> categories = activeOnly ?
            categoryService.getActiveRootCategories() :
            categoryService.getRootCategories();

        List<CategoryResponse> response = categories.stream()
            .map(cat -> categoryMapper.toResponse(cat, includeChildren))
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get category by ID")
    public ResponseEntity<CategoryResponse> getCategoryById(
            @Parameter(description = "Category ID")
            @PathVariable Long id,
            @Parameter(description = "Include children in response")
            @RequestParam(required = false, defaultValue = "true") boolean includeChildren
    ) {
        log.info("GET /api/v1/categories/{} - includeChildren: {}", id, includeChildren);

        Category category = categoryService.getCategoryById(id);
        CategoryResponse response = categoryMapper.toResponse(category, includeChildren);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get category by slug")
    public ResponseEntity<CategoryResponse> getCategoryBySlug(
            @Parameter(description = "Category slug")
            @PathVariable String slug,
            @Parameter(description = "Include children in response")
            @RequestParam(required = false, defaultValue = "true") boolean includeChildren
    ) {
        log.info("GET /api/v1/categories/slug/{} - includeChildren: {}", slug, includeChildren);

        Category category = categoryService.getCategoryBySlug(slug);
        CategoryResponse response = categoryMapper.toResponse(category, includeChildren);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/children")
    @Operation(summary = "Get children of a category")
    public ResponseEntity<List<CategoryResponse>> getCategoryChildren(
            @Parameter(description = "Parent category ID")
            @PathVariable Long id,
            @Parameter(description = "Include only active categories")
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly
    ) {
        log.info("GET /api/v1/categories/{}/children - activeOnly: {}", id, activeOnly);

        List<Category> children = activeOnly ?
            categoryService.getActiveCategoryChildren(id) :
            categoryService.getCategoryChildren(id);

        List<CategoryResponse> response = children.stream()
            .map(categoryMapper::toResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/subcategories")
    @Operation(summary = "Get all subcategories recursively")
    public ResponseEntity<List<CategoryResponse>> getAllSubcategories(
            @Parameter(description = "Parent category ID")
            @PathVariable Long id
    ) {
        log.info("GET /api/v1/categories/{}/subcategories", id);

        List<Category> subcategories = categoryService.getAllSubcategories(id);
        List<CategoryResponse> response = subcategories.stream()
            .map(categoryMapper::toResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    @Operation(summary = "Search categories by name")
    public ResponseEntity<List<CategoryResponse>> searchCategories(
            @Parameter(description = "Search term")
            @RequestParam String name
    ) {
        log.info("GET /api/v1/categories/search - name: {}", name);

        List<Category> categories = categoryService.searchCategories(name);
        List<CategoryResponse> response = categories.stream()
            .map(categoryMapper::toResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "Create a new category")
    public ResponseEntity<CategoryResponse> createCategory(
            @Valid @RequestBody CategoryRequest request
    ) {
        log.info("POST /api/v1/categories - name: {}, slug: {}", request.getName(), request.getSlug());

        Category category = categoryMapper.toEntity(request);
        Category created = categoryService.createCategory(category);
        CategoryResponse response = categoryMapper.toResponse(created);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a category")
    public ResponseEntity<CategoryResponse> updateCategory(
            @Parameter(description = "Category ID")
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request
    ) {
        log.info("PUT /api/v1/categories/{}", id);

        Category category = categoryMapper.toEntity(request);
        Category updated = categoryService.updateCategory(id, category);
        CategoryResponse response = categoryMapper.toResponse(updated);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/move")
    @Operation(summary = "Move a category to a new parent")
    public ResponseEntity<CategoryResponse> moveCategory(
            @Parameter(description = "Category ID to move")
            @PathVariable Long id,
            @Parameter(description = "New parent category ID (null for root level)")
            @RequestParam(required = false) Long newParentId
    ) {
        log.info("PATCH /api/v1/categories/{}/move - newParentId: {}", id, newParentId);

        Category moved = categoryService.moveCategory(id, newParentId);
        CategoryResponse response = categoryMapper.toResponse(moved);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a category (soft delete)")
    public ResponseEntity<Void> deleteCategory(
            @Parameter(description = "Category ID")
            @PathVariable Long id
    ) {
        log.info("DELETE /api/v1/categories/{}", id);

        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
