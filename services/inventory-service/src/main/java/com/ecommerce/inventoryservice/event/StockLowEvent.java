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
public class StockLowEvent {
    private String productId;
    private String sku;
    private Integer currentQuantity;
    private Integer reorderLevel;
    private Integer reorderQuantity;
    private LocalDateTime timestamp;
}
