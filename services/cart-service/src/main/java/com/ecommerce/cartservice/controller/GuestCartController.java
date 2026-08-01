package com.ecommerce.cartservice.controller;

import com.ecommerce.cartservice.dto.AddToCartRequest;
import com.ecommerce.cartservice.dto.CartResponse;
import com.ecommerce.cartservice.dto.UpdateCartItemRequest;
import com.ecommerce.cartservice.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Anonymous (guest) cart endpoints — the companion to order-service's guest
 * checkout (PR#138).
 *
 * <p>These paths require NO JWT: an unauthenticated shopper identifies their cart
 * by the email they will later check out with, passed in the {@code X-Guest-Email}
 * header. cart-service derives the owner id as
 * {@code guest:sha256(normalize(email))} via {@link CartService#guestCartOwnerId}
 * — the SAME identity order-service derives at checkout — so the order-creation
 * saga finds exactly the cart the guest built here. The client never sends the
 * derived id and cannot assert an arbitrary owner.</p>
 *
 * <p>Kept as a SEPARATE controller (not merged into {@link CartController}) so the
 * unauthenticated surface is explicit and carries no {@code @SecurityRequirement}.
 * The paths are opened at both the gateway and cart-service security layers,
 * scoped to these exact methods; abuse is bounded by the gateway's IP-keyed rate
 * limit. Email is passed via header (not the URL) to keep it out of access logs
 * and to work uniformly for GET/DELETE which carry no body.</p>
 */
@RestController
@RequestMapping("/api/cart/guest")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Guest Cart", description = "Anonymous shopping cart APIs (no authentication)")
public class GuestCartController {

    static final String GUEST_EMAIL_HEADER = "X-Guest-Email";

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "Get the guest cart", description = "Get or create the anonymous cart keyed by the guest email")
    public ResponseEntity<CartResponse> getGuestCart(
            @Parameter(description = "Guest email; the cart owner is derived from it")
            @RequestHeader(GUEST_EMAIL_HEADER) String guestEmail) {
        String ownerId = cartService.guestCartOwnerId(guestEmail);
        log.debug("GET /api/cart/guest - derived owner: {}", ownerId);
        return ResponseEntity.ok(cartService.getOrCreateCart(ownerId));
    }

    @PostMapping("/items")
    @Operation(summary = "Add item to the guest cart")
    public ResponseEntity<CartResponse> addItem(
            @RequestHeader(GUEST_EMAIL_HEADER) String guestEmail,
            @Valid @RequestBody AddToCartRequest request) {
        String ownerId = cartService.guestCartOwnerId(guestEmail);
        log.debug("POST /api/cart/guest/items - derived owner: {}, productId: {}", ownerId, request.getProductId());
        return ResponseEntity.ok(cartService.addItemToCart(ownerId, request));
    }

    @PutMapping("/items/{itemId}")
    @Operation(summary = "Update a guest cart item quantity")
    public ResponseEntity<CartResponse> updateItem(
            @RequestHeader(GUEST_EMAIL_HEADER) String guestEmail,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        String ownerId = cartService.guestCartOwnerId(guestEmail);
        log.debug("PUT /api/cart/guest/items/{} - derived owner: {}", itemId, ownerId);
        return ResponseEntity.ok(cartService.updateCartItem(ownerId, itemId, request));
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Remove an item from the guest cart")
    public ResponseEntity<CartResponse> removeItem(
            @RequestHeader(GUEST_EMAIL_HEADER) String guestEmail,
            @PathVariable Long itemId) {
        String ownerId = cartService.guestCartOwnerId(guestEmail);
        log.debug("DELETE /api/cart/guest/items/{} - derived owner: {}", itemId, ownerId);
        return ResponseEntity.ok(cartService.removeItemFromCart(ownerId, itemId));
    }

    @DeleteMapping("/clear")
    @Operation(summary = "Clear the guest cart")
    public ResponseEntity<Void> clear(
            @RequestHeader(GUEST_EMAIL_HEADER) String guestEmail) {
        String ownerId = cartService.guestCartOwnerId(guestEmail);
        log.debug("DELETE /api/cart/guest/clear - derived owner: {}", ownerId);
        cartService.clearCart(ownerId);
        return ResponseEntity.noContent().build();
    }
}
