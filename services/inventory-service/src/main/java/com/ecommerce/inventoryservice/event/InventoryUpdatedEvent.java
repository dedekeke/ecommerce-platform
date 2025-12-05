package com.ecommerce.inventoryservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryUpdatedEvent {
    private String productId;
    private String sku;
    private Integer quantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    private String status;
    private String updateType;
    private String reason;
    private LocalDateTime timestamp;
}
