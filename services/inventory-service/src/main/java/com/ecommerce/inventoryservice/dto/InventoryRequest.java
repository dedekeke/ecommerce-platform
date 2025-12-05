package com.ecommerce.inventoryservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryRequest {

    @NotBlank(message = "Product ID is required")
    private String productId;

    @NotBlank(message = "SKU is required")
    private String sku;

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity must be non-negative")
    private Integer quantity;

    @NotNull(message = "Reorder level is required")
    @Min(value = 0, message = "Reorder level must be non-negative")
    private Integer reorderLevel;

    @NotNull(message = "Reorder quantity is required")
    @Min(value = 0, message = "Reorder quantity must be non-negative")
    private Integer reorderQuantity;
}
