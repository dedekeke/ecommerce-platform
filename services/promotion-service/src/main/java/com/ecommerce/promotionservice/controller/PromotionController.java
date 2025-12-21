package com.ecommerce.promotionservice.controller;

import com.ecommerce.promotionservice.dto.*;
import com.ecommerce.promotionservice.service.PromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Promotion Management", description = "APIs for managing promotions and discounts")
public class PromotionController {

    private final PromotionService promotionService;

    @GetMapping
    @Operation(summary = "Get all active promotions", description = "Retrieve all currently active promotions")
    public ResponseEntity<List<PromotionResponse>> getAllActivePromotions() {
        log.debug("GET /api/promotions - Fetching all active promotions");
        List<PromotionResponse> promotions = promotionService.getAllActivePromotions();
        return ResponseEntity.ok(promotions);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get promotion by ID", description = "Retrieve a specific promotion by its ID")
    public ResponseEntity<PromotionResponse> getPromotionById(@PathVariable Long id) {
        log.debug("GET /api/promotions/{} - Fetching promotion by id", id);
        PromotionResponse promotion = promotionService.getPromotionById(id);
        return ResponseEntity.ok(promotion);
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Get promotion by code", description = "Retrieve a specific promotion by its code")
    public ResponseEntity<PromotionResponse> getPromotionByCode(@PathVariable String code) {
        log.debug("GET /api/promotions/code/{} - Fetching promotion by code", code);
        PromotionResponse promotion = promotionService.getPromotionByCode(code);
        return ResponseEntity.ok(promotion);
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate promotion", description = "Validate a promotion code for a purchase")
    public ResponseEntity<DiscountResult> validatePromotion(@Valid @RequestBody PromotionValidationRequest request) {
        log.debug("POST /api/promotions/validate - Validating promotion: {}", request.getCode());
        DiscountResult result = promotionService.validatePromotion(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/apply")
    @Operation(summary = "Apply promotion", description = "Apply a promotion code to a purchase and increment usage count")
    public ResponseEntity<DiscountResult> applyPromotion(@Valid @RequestBody PromotionValidationRequest request) {
        log.debug("POST /api/promotions/apply - Applying promotion: {}", request.getCode());
        DiscountResult result = promotionService.applyPromotion(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping
    @Operation(summary = "Create promotion (Admin)", description = "Create a new promotion (requires admin role)")
    public ResponseEntity<PromotionResponse> createPromotion(@Valid @RequestBody PromotionRequest request) {
        log.info("POST /api/promotions - Creating new promotion: {}", request.getCode());
        PromotionResponse promotion = promotionService.createPromotion(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(promotion);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update promotion (Admin)", description = "Update an existing promotion (requires admin role)")
    public ResponseEntity<PromotionResponse> updatePromotion(
            @PathVariable Long id,
            @Valid @RequestBody PromotionRequest request) {
        log.info("PUT /api/promotions/{} - Updating promotion", id);
        PromotionResponse promotion = promotionService.updatePromotion(id, request);
        return ResponseEntity.ok(promotion);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete promotion (Admin)", description = "Delete a promotion (requires admin role)")
    public ResponseEntity<Void> deletePromotion(@PathVariable Long id) {
        log.info("DELETE /api/promotions/{} - Deleting promotion", id);
        promotionService.deletePromotion(id);
        return ResponseEntity.noContent().build();
    }
}
