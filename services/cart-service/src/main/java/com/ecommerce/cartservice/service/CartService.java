package com.ecommerce.cartservice.service;

import com.ecommerce.cartservice.client.ProductServiceClient;
import com.ecommerce.cartservice.domain.Cart;
import com.ecommerce.cartservice.domain.CartItem;
import com.ecommerce.cartservice.domain.CartStatus;
import com.ecommerce.cartservice.dto.*;
import com.ecommerce.cartservice.exception.CartNotFoundException;
import com.ecommerce.cartservice.exception.CartItemNotFoundException;
import com.ecommerce.cartservice.exception.ProductNotAvailableException;
import com.ecommerce.cartservice.repository.CartItemRepository;
import com.ecommerce.cartservice.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Cart Service
 *
 * Business logic for cart management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductServiceClient productServiceClient;

    private static final int CART_EXPIRATION_DAYS = 30;

    /**
     * Get or create active cart for user
     */
    @Transactional
    public CartResponse getOrCreateCart(String userId) {
        log.debug("Getting or creating cart for user: {}", userId);

        Cart cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseGet(() -> createNewCart(userId));

        return toCartResponse(cart);
    }

    /**
     * Add item to cart
     */
    @Transactional
    public CartResponse addItemToCart(String userId, AddToCartRequest request) {
        log.debug("Adding item to cart - userId: {}, productId: {}, quantity: {}",
                userId, request.getProductId(), request.getQuantity());

        // Get product details from product service
        ProductDto product = getProductOrThrow(request.getProductId());

        // Validate product is available
        validateProductAvailability(product, request.getQuantity());

        // Get or create cart
        Cart cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseGet(() -> createNewCart(userId));

        // Check if product already exists in cart
        CartItem existingItem = cartItemRepository
                .findByCartIdAndProductId(cart.getId(), request.getProductId())
                .orElse(null);

        if (existingItem != null) {
            // Update quantity of existing item
            int newQuantity = existingItem.getQuantity() + request.getQuantity();
            validateProductAvailability(product, newQuantity);
            existingItem.updateQuantity(newQuantity);
            cartItemRepository.save(existingItem);
        } else {
            // Create new cart item
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .productId(product.getId())
                    .productName(product.getName())
                    .productSku(product.getSku())
                    .productImageUrl(product.getImageUrl())
                    .priceSnapshot(product.getPrice())
                    .quantity(request.getQuantity())
                    .build();
            newItem.calculateSubtotal();
            cart.addItem(newItem);
            cartItemRepository.save(newItem);
        }

        // Update cart expiration
        cart.setExpiresAt(Instant.now().plus(CART_EXPIRATION_DAYS, ChronoUnit.DAYS));
        cart.recalculateTotals();
        Cart savedCart = cartRepository.save(cart);

        log.info("Item added to cart - cartId: {}, productId: {}, quantity: {}",
                savedCart.getId(), request.getProductId(), request.getQuantity());

        return toCartResponse(savedCart);
    }

    /**
     * Update cart item quantity
     */
    @Transactional
    public CartResponse updateCartItem(String userId, Long itemId, UpdateCartItemRequest request) {
        log.debug("Updating cart item - userId: {}, itemId: {}, quantity: {}",
                userId, itemId, request.getQuantity());

        CartItem cartItem = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new CartItemNotFoundException("Cart item not found: " + itemId));

        // Verify cart belongs to user
        Cart cart = cartItem.getCart();
        if (!cart.getUserId().equals(userId)) {
            throw new CartNotFoundException("Cart not found for user");
        }

        // Validate product availability
        ProductDto product = getProductOrThrow(cartItem.getProductId());
        validateProductAvailability(product, request.getQuantity());

        // Update quantity
        cartItem.updateQuantity(request.getQuantity());
        cartItemRepository.save(cartItem);

        // Update cart
        cart.setExpiresAt(Instant.now().plus(CART_EXPIRATION_DAYS, ChronoUnit.DAYS));
        cart.recalculateTotals();
        Cart savedCart = cartRepository.save(cart);

        log.info("Cart item updated - cartId: {}, itemId: {}, quantity: {}",
                savedCart.getId(), itemId, request.getQuantity());

        return toCartResponse(savedCart);
    }

    /**
     * Remove item from cart
     */
    @Transactional
    public CartResponse removeItemFromCart(String userId, Long itemId) {
        log.debug("Removing cart item - userId: {}, itemId: {}", userId, itemId);

        CartItem cartItem = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new CartItemNotFoundException("Cart item not found: " + itemId));

        // Verify cart belongs to user
        Cart cart = cartItem.getCart();
        if (!cart.getUserId().equals(userId)) {
            throw new CartNotFoundException("Cart not found for user");
        }

        // Remove item
        cart.removeItem(cartItem);
        cartItemRepository.delete(cartItem);

        // Update cart
        cart.recalculateTotals();
        Cart savedCart = cartRepository.save(cart);

        log.info("Cart item removed - cartId: {}, itemId: {}", savedCart.getId(), itemId);

        return toCartResponse(savedCart);
    }

    /**
     * Clear all items from cart
     */
    @Transactional
    public void clearCart(String userId) {
        log.debug("Clearing cart for user: {}", userId);

        Cart cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new CartNotFoundException("Active cart not found for user"));

        cart.clear();
        cartRepository.save(cart);

        log.info("Cart cleared - cartId: {}", cart.getId());
    }

    /**
     * Delete cart
     */
    @Transactional
    public void deleteCart(String userId) {
        log.debug("Deleting cart for user: {}", userId);

        Cart cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new CartNotFoundException("Active cart not found for user"));

        cartRepository.delete(cart);

        log.info("Cart deleted - cartId: {}", cart.getId());
    }

    /**
     * Mark cart as checked out
     */
    @Transactional
    public void checkoutCart(String userId) {
        log.debug("Checking out cart for user: {}", userId);

        Cart cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new CartNotFoundException("Active cart not found for user"));

        cart.markAsCheckedOut();
        cartRepository.save(cart);

        log.info("Cart checked out - cartId: {}", cart.getId());
    }

    // Helper methods

    private Cart createNewCart(String userId) {
        Cart cart = Cart.builder()
                .userId(userId)
                .status(CartStatus.ACTIVE)
                .expiresAt(Instant.now().plus(CART_EXPIRATION_DAYS, ChronoUnit.DAYS))
                .build();
        return cartRepository.save(cart);
    }

    private ProductDto getProductOrThrow(String productId) {
        try {
            return productServiceClient.getProductById(productId);
        } catch (Exception e) {
            log.error("Failed to fetch product: {}", productId, e);
            throw new ProductNotAvailableException("Product not available: " + productId);
        }
    }

    private void validateProductAvailability(ProductDto product, int requestedQuantity) {
        if (!"ACTIVE".equals(product.getStatus())) {
            throw new ProductNotAvailableException("Product is not available: " + product.getId());
        }
        if (product.getStockQuantity() < requestedQuantity) {
            throw new ProductNotAvailableException(
                    String.format("Insufficient stock for product %s. Available: %d, Requested: %d",
                            product.getId(), product.getStockQuantity(), requestedQuantity));
        }
    }

    private CartResponse toCartResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(this::toCartItemResponse)
                .collect(Collectors.toList());

        return CartResponse.builder()
                .id(cart.getId() != null ? cart.getId().toString() : null)
                .userId(cart.getUserId())
                .items(items)
                .totalAmount(cart.getTotalAmount())
                .totalItems(cart.getTotalItems())
                .status(cart.getStatus().name())
                .expiresAt(cart.getExpiresAt())
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .build();
    }

    private CartItemResponse toCartItemResponse(CartItem item) {
        return CartItemResponse.builder()
                .id(item.getId() != null ? item.getId().toString() : null)
                .productId(item.getProductId() != null ? item.getProductId().toString() : null)
                .productName(item.getProductName())
                .productSku(item.getProductSku())
                .productImageUrl(item.getProductImageUrl())
                .price(item.getPriceSnapshot())
                .quantity(item.getQuantity())
                .subtotal(item.getSubtotal())
                .addedAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
