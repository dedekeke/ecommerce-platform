package com.ecommerce.inventoryservice.mapper;

import com.ecommerce.inventoryservice.domain.entity.Inventory;
import com.ecommerce.inventoryservice.dto.InventoryRequest;
import com.ecommerce.inventoryservice.dto.InventoryResponse;
import org.springframework.stereotype.Component;

@Component
public class InventoryMapper {

    /**
     * Convert Inventory entity to InventoryResponse DTO
     */
    public InventoryResponse toResponse(Inventory inventory) {
        if (inventory == null) {
            return null;
        }

        return InventoryResponse.builder()
                .id(inventory.getId())
                .productId(inventory.getProductId())
                .sku(inventory.getSku())
                .quantity(inventory.getQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .availableQuantity(inventory.getAvailableQuantity())
                .status(inventory.getStatus().name())
                .reorderLevel(inventory.getReorderLevel())
                .reorderQuantity(inventory.getReorderQuantity())
                .lastRestockedAt(inventory.getLastRestockedAt())
                .createdAt(inventory.getCreatedAt())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }

    /**
     * Convert InventoryRequest DTO to Inventory entity
     */
    public Inventory toEntity(InventoryRequest request) {
        if (request == null) {
            return null;
        }

        return Inventory.builder()
                .productId(request.getProductId())
                .sku(request.getSku())
                .quantity(request.getQuantity())
                .reservedQuantity(0)
                .reorderLevel(request.getReorderLevel())
                .reorderQuantity(request.getReorderQuantity())
                .build();
    }
}
