package com.ecommerce.inventoryservice.mapper;

import com.ecommerce.common.grpc.inventory.InventoryResponse;
import com.ecommerce.inventoryservice.domain.entity.Inventory;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

@Component
public class InventoryGrpcMapper {

    /**
     * Build gRPC InventoryResponse from Inventory entity
     */
    public InventoryResponse buildInventoryResponse(Inventory inventory, boolean success, String message) {
        if (inventory == null) {
            return InventoryResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Inventory not found")
                    .build();
        }

        InventoryResponse.Builder builder = InventoryResponse.newBuilder()
                .setProductId(inventory.getProductId())
                .setSku(inventory.getSku())
                .setQuantity(inventory.getQuantity())
                .setReservedQuantity(inventory.getReservedQuantity())
                .setAvailableQuantity(inventory.getAvailableQuantity())
                .setStatus(mapInventoryStatus(inventory.getStatus()))
                .setReorderLevel(inventory.getReorderLevel())
                .setReorderQuantity(inventory.getReorderQuantity())
                .setSuccess(success)
                .setMessage(message);

        if (inventory.getLastRestockedAt() != null) {
            builder.setLastRestockedAt(inventory.getLastRestockedAt().toEpochSecond(ZoneOffset.UTC));
        }

        return builder.build();
    }

    /**
     * Map domain ReservationStatus to gRPC ReservationStatus
     */
    public com.ecommerce.common.grpc.inventory.ReservationStatus mapReservationStatus(
            com.ecommerce.inventoryservice.domain.enums.ReservationStatus status) {
        if (status == null) {
            return com.ecommerce.common.grpc.inventory.ReservationStatus.RESERVATION_STATUS_UNSPECIFIED;
        }

        return switch (status) {
            case RESERVED -> com.ecommerce.common.grpc.inventory.ReservationStatus.RESERVED;
            case COMMITTED -> com.ecommerce.common.grpc.inventory.ReservationStatus.COMMITTED;
            case RELEASED -> com.ecommerce.common.grpc.inventory.ReservationStatus.RELEASED;
            case EXPIRED -> com.ecommerce.common.grpc.inventory.ReservationStatus.EXPIRED;
        };
    }

    /**
     * Map domain InventoryStatus to gRPC InventoryStatus
     */
    public com.ecommerce.common.grpc.inventory.InventoryStatus mapInventoryStatus(
            com.ecommerce.inventoryservice.domain.enums.InventoryStatus status) {
        if (status == null) {
            return com.ecommerce.common.grpc.inventory.InventoryStatus.INVENTORY_STATUS_UNSPECIFIED;
        }

        return switch (status) {
            case IN_STOCK -> com.ecommerce.common.grpc.inventory.InventoryStatus.IN_STOCK;
            case LOW_STOCK -> com.ecommerce.common.grpc.inventory.InventoryStatus.LOW_STOCK;
            case OUT_OF_STOCK -> com.ecommerce.common.grpc.inventory.InventoryStatus.OUT_OF_STOCK;
            case DISCONTINUED -> com.ecommerce.common.grpc.inventory.InventoryStatus.DISCONTINUED;
        };
    }
}
