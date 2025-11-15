package com.ecommerce.cartservice.controller;

import com.ecommerce.cartservice.dto.AddToCartRequest;
import com.ecommerce.cartservice.dto.CartResponse;
import com.ecommerce.cartservice.dto.UpdateCartItemRequest;
import com.ecommerce.cartservice.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Cart Controller
 *
 * REST endpoints for cart management.
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Cart", description = "Shopping cart management APIs")
@SecurityRequirement(name = "bearer-auth")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "Get current user's cart", description = "Get or create active cart for the authenticated user")
    public ResponseEntity<CartResponse> getCart(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        log.debug("GET /api/cart - userId: {}", userId);

        CartResponse cart = cartService.getOrCreateCart(userId);
        return ResponseEntity.ok(cart);
    }

    @PostMapping("/items")
    @Operation(summary = "Add item to cart", description = "Add a product to the user's cart or update quantity if already exists")
    public ResponseEntity<CartResponse> addItemToCart(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddToCartRequest request) {
        String userId = jwt.getSubject();
        log.debug("POST /api/cart/items - userId: {}, request: {}", userId, request);

        CartResponse cart = cartService.addItemToCart(userId, request);
        return ResponseEntity.ok(cart);
    }

    @PutMapping("/items/{itemId}")
    @Operation(summary = "Update cart item", description = "Update the quantity of a cart item")
    public ResponseEntity<CartResponse> updateCartItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        String userId = jwt.getSubject();
        log.debug("PUT /api/cart/items/{} - userId: {}, request: {}", itemId, userId, request);

        CartResponse cart = cartService.updateCartItem(userId, itemId, request);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Remove item from cart", description = "Remove a specific item from the cart")
    public ResponseEntity<CartResponse> removeItemFromCart(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long itemId) {
        String userId = jwt.getSubject();
        log.debug("DELETE /api/cart/items/{} - userId: {}", itemId, userId);

        CartResponse cart = cartService.removeItemFromCart(userId, itemId);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/clear")
    @Operation(summary = "Clear cart", description = "Remove all items from the cart")
    public ResponseEntity<Void> clearCart(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        log.debug("DELETE /api/cart/clear - userId: {}", userId);

        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @Operation(summary = "Delete cart", description = "Delete the entire cart")
    public ResponseEntity<Void> deleteCart(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        log.debug("DELETE /api/cart - userId: {}", userId);

        cartService.deleteCart(userId);
        return ResponseEntity.noContent().build();
    }
}
