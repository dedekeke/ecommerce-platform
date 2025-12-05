package com.ecommerce.inventoryservice.rest;

import com.ecommerce.inventoryservice.domain.entity.Inventory;
import com.ecommerce.inventoryservice.dto.InventoryRequest;
import com.ecommerce.inventoryservice.dto.InventoryResponse;
import com.ecommerce.inventoryservice.dto.StockUpdateRequest;
import com.ecommerce.inventoryservice.mapper.InventoryMapper;
import com.ecommerce.inventoryservice.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Inventory", description = "Inventory management endpoints")
@SecurityRequirement(name = "bearer-auth")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryMapper inventoryMapper;

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @Operation(summary = "Create inventory", description = "Create inventory for a new product (Admin only)")
    public ResponseEntity<InventoryResponse> createInventory(@Valid @RequestBody InventoryRequest request) {
        log.info("Creating inventory for product: {}", request.getProductId());

        Inventory inventory = inventoryMapper.toEntity(request);
        Inventory saved = inventoryService.createOrUpdateInventory(inventory);

        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryMapper.toResponse(saved));
    }

    @GetMapping("/product/{productId}")
    @Operation(summary = "Get inventory by product ID", description = "Retrieve inventory information for a product")
    public ResponseEntity<InventoryResponse> getInventoryByProductId(@PathVariable String productId) {
        log.info("Getting inventory for product: {}", productId);

        Inventory inventory = inventoryService.getInventoryByProductId(productId);

        return ResponseEntity.ok(inventoryMapper.toResponse(inventory));
    }

    @GetMapping("/sku/{sku}")
    @Operation(summary = "Get inventory by SKU", description = "Retrieve inventory information by SKU")
    public ResponseEntity<InventoryResponse> getInventoryBySku(@PathVariable String sku) {
        log.info("Getting inventory for SKU: {}", sku);

        Inventory inventory = inventoryService.getInventoryBySku(sku);

        return ResponseEntity.ok(inventoryMapper.toResponse(inventory));
    }

    @GetMapping("/check-availability/{productId}")
    @Operation(summary = "Check availability", description = "Check if product is available in requested quantity")
    public ResponseEntity<Boolean> checkAvailability(
            @PathVariable String productId,
            @RequestParam Integer quantity) {
        log.info("Checking availability for product {} with quantity {}", productId, quantity);

        boolean available = inventoryService.checkAvailability(productId, quantity);

        return ResponseEntity.ok(available);
    }

    @PutMapping("/product/{productId}/stock")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @Operation(summary = "Update stock", description = "Update stock quantity for a product (Admin only)")
    public ResponseEntity<InventoryResponse> updateStock(
            @PathVariable String productId,
            @Valid @RequestBody StockUpdateRequest request) {
        log.info("Updating stock for product {}: {} ({})", productId, request.getQuantityChange(), request.getUpdateType());

        Inventory updated = inventoryService.updateStock(
                productId,
                request.getQuantityChange(),
                request.getUpdateType(),
                request.getNotes()
        );

        return ResponseEntity.ok(inventoryMapper.toResponse(updated));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @Operation(summary = "Get low stock items", description = "Get all items with low or out of stock status (Admin only)")
    public ResponseEntity<List<InventoryResponse>> getLowStockItems() {
        log.info("Getting low stock items");

        List<Inventory> lowStockItems = inventoryService.getLowStockItems();

        List<InventoryResponse> responses = lowStockItems.stream()
                .map(inventoryMapper::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/reorder-needed")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @Operation(summary = "Get items needing reorder", description = "Get all items that need to be reordered (Admin only)")
    public ResponseEntity<List<InventoryResponse>> getItemsNeedingReorder() {
        log.info("Getting items needing reorder");

        List<Inventory> items = inventoryService.getItemsNeedingReorder();

        List<InventoryResponse> responses = items.stream()
                .map(inventoryMapper::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }
}
