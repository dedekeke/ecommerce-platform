package com.ecommerce.inventoryservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Spring {@link org.springframework.context.ApplicationEvent} fired by
 * {@link com.ecommerce.inventoryservice.service.InventoryService} every time a
 * stock level moves. Consumed in-process by the SSE fan-out so the broker is
 * not required to be available for browsers to receive live updates.
 *
 * <p>Carries both the previous and the new available quantity so the frontend
 * can show qty-change animations without any extra bookkeeping.
 */
@Data
@Builder
@AllArgsConstructor
public class InventoryStockChangedEvent {

    private final String productId;
    private final String sku;
    private final Integer availableQty;
    private final Integer previousQty;
    private final LocalDateTime timestamp;
}
