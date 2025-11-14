package com.ecommerce.productservice.mapper;

import com.ecommerce.productservice.dto.ProductRequest;
import com.ecommerce.productservice.dto.ProductResponse;
import com.ecommerce.productservice.model.Category;
import com.ecommerce.productservice.model.Product;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between Product entities and DTOs.
 */
@Component
public class ProductMapper {

    private final CategoryMapper categoryMapper;

    public ProductMapper(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    /**
     * Convert ProductRequest to Product entity.
     */
    public Product toEntity(ProductRequest request) {
        if (request == null) {
            return null;
        }

        Product.ProductBuilder builder = Product.builder()
            .sku(request.getSku())
            .name(request.getName())
            .description(request.getDescription())
            .price(request.getPrice())
            .currency(request.getCurrency())
            .images(request.getImages())
            .dimensions(request.getDimensions())
            .stockQuantity(request.getStockQuantity() != null ? request.getStockQuantity() : 0)
            .active(request.getActive() != null ? request.getActive() : true);

        // Category will be set by the service layer
        if (request.getCategoryId() != null) {
            Category category = new Category();
            category.setId(request.getCategoryId());
            builder.category(category);
        }

        return builder.build();
    }

    /**
     * Convert Product entity to ProductResponse.
     */
    public ProductResponse toResponse(Product product) {
        if (product == null) {
            return null;
        }

        return ProductResponse.builder()
            .id(product.getId())
            .sku(product.getSku())
            .name(product.getName())
            .description(product.getDescription())
            .category(product.getCategory() != null ? categoryMapper.toResponse(product.getCategory()) : null)
            .price(product.getPrice())
            .currency(product.getCurrency())
            .images(product.getImages())
            .dimensions(product.getDimensions())
            .stockQuantity(product.getStockQuantity())
            .active(product.getActive())
            .createdAt(product.getCreatedAt())
            .updatedAt(product.getUpdatedAt())
            .inStock(product.isInStock())
            .available(product.isAvailable())
            .build();
    }

    /**
     * Update existing Product entity with values from ProductRequest.
     */
    public void updateEntity(Product product, ProductRequest request) {
        if (product == null || request == null) {
            return;
        }

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCurrency(request.getCurrency());
        product.setImages(request.getImages());
        product.setDimensions(request.getDimensions());
        product.setActive(request.getActive() != null ? request.getActive() : product.getActive());

        if (request.getStockQuantity() != null) {
            product.setStockQuantity(request.getStockQuantity());
        }

        // Category will be updated by service layer if categoryId is provided
    }
}
