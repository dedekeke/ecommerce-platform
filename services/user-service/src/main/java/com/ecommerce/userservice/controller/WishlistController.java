package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.domain.Wishlist;
import com.ecommerce.userservice.dto.AddWishlistItemRequest;
import com.ecommerce.userservice.dto.WishlistItemResponse;
import com.ecommerce.userservice.service.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Wishlist REST endpoints. Authentication is required for every method; the
 * service layer additionally enforces that the JWT subject corresponds to the
 * {@code userId} in the path.
 */
@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Wishlist", description = "User wishlist management endpoints")
@SecurityRequirement(name = "bearer-jwt")
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping("/{userId}")
    @Operation(summary = "Get wishlist", description = "Get the authenticated user's wishlist")
    public ResponseEntity<List<WishlistItemResponse>> getWishlist(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt) {

        String callerAuth0Id = jwt.getSubject();
        log.debug("GET /api/wishlist/{} caller={}", userId, callerAuth0Id);

        List<WishlistItemResponse> items = wishlistService.getWishlist(userId, callerAuth0Id)
                .stream()
                .map(WishlistItemResponse::from)
                .toList();

        return ResponseEntity.ok(items);
    }

    @PostMapping("/{userId}/items")
    @Operation(summary = "Add wishlist item", description = "Add a product to the wishlist")
    public ResponseEntity<WishlistItemResponse> addItem(
            @PathVariable Long userId,
            @Valid @RequestBody AddWishlistItemRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String callerAuth0Id = jwt.getSubject();
        log.debug("POST /api/wishlist/{}/items productId={} caller={}",
                userId, request.getProductId(), callerAuth0Id);

        Wishlist saved = wishlistService.addItem(userId, request.getProductId(), callerAuth0Id);
        return ResponseEntity.status(HttpStatus.CREATED).body(WishlistItemResponse.from(saved));
    }

    @DeleteMapping("/{userId}/items/{productId}")
    @Operation(summary = "Remove wishlist item", description = "Remove a product from the wishlist")
    public ResponseEntity<Void> removeItem(
            @PathVariable Long userId,
            @PathVariable String productId,
            @AuthenticationPrincipal Jwt jwt) {

        String callerAuth0Id = jwt.getSubject();
        log.debug("DELETE /api/wishlist/{}/items/{} caller={}", userId, productId, callerAuth0Id);

        wishlistService.removeItem(userId, productId, callerAuth0Id);
        return ResponseEntity.noContent().build();
    }
}
