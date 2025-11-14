package com.ecommerce.productservice.mapper;

import com.ecommerce.productservice.dto.CategoryRequest;
import com.ecommerce.productservice.dto.CategoryResponse;
import com.ecommerce.productservice.model.Category;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper for converting between Category entities and DTOs.
 */
@Component
public class CategoryMapper {

    /**
     * Convert CategoryRequest to Category entity.
     */
    public Category toEntity(CategoryRequest request) {
        if (request == null) {
            return null;
        }

        Category.CategoryBuilder builder = Category.builder()
            .name(request.getName())
            .slug(request.getSlug())
            .description(request.getDescription())
            .imageUrl(request.getImageUrl())
            .active(request.getActive() != null ? request.getActive() : true)
            .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);

        // Parent will be set by the service layer
        if (request.getParentId() != null) {
            Category parent = new Category();
            parent.setId(request.getParentId());
            builder.parent(parent);
        }

        return builder.build();
    }

    /**
     * Convert Category entity to CategoryResponse.
     */
    public CategoryResponse toResponse(Category category) {
        return toResponse(category, false);
    }

    /**
     * Convert Category entity to CategoryResponse with optional children.
     */
    public CategoryResponse toResponse(Category category, boolean includeChildren) {
        if (category == null) {
            return null;
        }

        CategoryResponse.CategoryResponseBuilder builder = CategoryResponse.builder()
            .id(category.getId())
            .name(category.getName())
            .slug(category.getSlug())
            .description(category.getDescription())
            .parentId(category.getParent() != null ? category.getParent().getId() : null)
            .parentName(category.getParent() != null ? category.getParent().getName() : null)
            .imageUrl(category.getImageUrl())
            .active(category.getActive())
            .displayOrder(category.getDisplayOrder())
            .createdAt(category.getCreatedAt())
            .updatedAt(category.getUpdatedAt())
            .level(category.getLevel())
            .fullPath(category.getFullPath())
            .hasChildren(category.hasChildren());

        // Include children if requested
        if (includeChildren && category.hasChildren()) {
            List<CategoryResponse> children = category.getChildren().stream()
                .map(child -> toResponse(child, false))  // Don't recursively include all children
                .collect(Collectors.toList());
            builder.children(children);
        } else {
            builder.children(new ArrayList<>());
        }

        return builder.build();
    }

    /**
     * Update existing Category entity with values from CategoryRequest.
     */
    public void updateEntity(Category category, CategoryRequest request) {
        if (category == null || request == null) {
            return;
        }

        category.setName(request.getName());
        category.setSlug(request.getSlug());
        category.setDescription(request.getDescription());
        category.setImageUrl(request.getImageUrl());
        category.setActive(request.getActive() != null ? request.getActive() : category.getActive());
        category.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : category.getDisplayOrder());

        // Parent will be updated by service layer if parentId is provided
    }
}
