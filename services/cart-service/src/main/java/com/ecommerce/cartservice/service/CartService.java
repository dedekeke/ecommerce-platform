package com.ecommerce.cartservice.service;

import com.ecommerce.cartservice.client.ProductServiceGateway;
import com.ecommerce.cartservice.client.UserServiceClient;
import com.ecommerce.cartservice.domain.Cart;
import com.ecommerce.cartservice.domain.CartItem;
import com.ecommerce.cartservice.domain.CartStatus;
import com.ecommerce.cartservice.dto.*;
import com.ecommerce.cartservice.exception.CartNotFoundException;
import com.ecommerce.cartservice.exception.CartItemNotFoundException;
import com.ecommerce.cartservice.exception.ProductNotAvailableException;
import com.ecommerce.cartservice.exception.ProductServiceUnavailableException;
import com.ecommerce.cartservice.repository.CartItemRepository;
import com.ecommerce.cartservice.repository.CartRepository;
import com.ecommerce.cartservice.security.GuestIdentityFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
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
    private final ProductServiceGateway productServiceGateway;
    private final UserServiceClient userServiceClient;
    private final GuestIdentityFactory guestIdentityFactory;

    private static final int CART_EXPIRATION_DAYS = 30;

    /** Lightweight edge check for the guest email (mirrors jakarta @Email intent). */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

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

        // Denormalise the shopper email on first add so the abandonment scanner
        // has a recipient. Backfills existing carts created before this column.
        ensureUserEmail(cart);

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

    /**
     * Resolve the opaque owner id for an anonymous (guest) cart from the guest's
     * email. Delegates to {@link GuestIdentityFactory} so the value is identical
     * to the one order-service derives at checkout — this is the whole reason the
     * guest's cart is findable by the order-creation saga.
     *
     * <p>The email is format-validated here (the single choke point for every
     * guest-cart endpoint) so a malformed/blank {@code X-Guest-Email} header is
     * rejected as HTTP 400 rather than silently keying a junk cart. Validation is
     * done here rather than in the factory to keep the factory's hashing input
     * byte-for-byte identical to order-service.</p>
     */
    public String guestCartOwnerId(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("Guest email must be a valid email address");
        }
        return guestIdentityFactory.guestId(email);
    }

    /**
     * Merge-on-login (claim seam): fold the caller's OWN anonymous guest cart into
     * their authenticated cart. Mirrors order-service's guest-order claim
     * ({@code OrderService.claimGuestOrders}).
     *
     * <p><b>Security:</b> the guest email is NOT accepted from the client. It is
     * resolved server-side from the authenticated caller's verified email
     * (user-service) and the guest identity re-derived from it. A caller can
     * therefore only ever claim a guest cart they themselves built under their own
     * verified email — they cannot supply a victim's email to steal (and delete)
     * someone else's guest cart. If the caller's verified email is unresolvable
     * there is nothing safe to claim, so this is a no-op returning the user cart.</p>
     *
     * <p>For every guest line item: if the user already has that product, the
     * quantities are summed; otherwise the item is recreated on the user's cart,
     * preserving the guest's price snapshot. The guest cart is then deleted so it
     * can neither be checked out nor merged twice.</p>
     *
     * <p>Deliberately makes NO product-service call: a login merge must not fail
     * because product-service is briefly unavailable, and cart lines are soft
     * holds whose availability is re-validated at checkout. Idempotent and safe to
     * call on every login (empty/absent guest cart → no-op).</p>
     */
    @Transactional
    public CartResponse mergeGuestCartIntoUser(String userId) {
        Optional<String> verifiedEmail = resolveUserEmail(userId);
        if (verifiedEmail.isEmpty()) {
            // No trustworthy email to derive the caller's own guest identity from —
            // never fall back to a client-supplied value. Nothing to claim.
            log.warn("Skipping guest-cart merge for {}: no verified email resolvable", userId);
            return getOrCreateCart(userId);
        }

        String guestId = guestIdentityFactory.guestId(verifiedEmail.get());
        if (guestId.equals(userId)) {
            // Defensive: an authenticated user id is an Auth0 sub and can never
            // carry the guest: prefix, but never merge a cart into itself.
            return getOrCreateCart(userId);
        }

        Optional<Cart> guestCartOpt = cartRepository.findByUserIdAndStatus(guestId, CartStatus.ACTIVE);
        if (guestCartOpt.isEmpty() || guestCartOpt.get().isEmpty()) {
            log.debug("No non-empty guest cart to merge for user {}", userId);
            guestCartOpt.ifPresent(cartRepository::delete);
            return getOrCreateCart(userId);
        }

        Cart guestCart = guestCartOpt.get();
        Cart userCart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseGet(() -> createNewCart(userId));

        int mergedItems = guestCart.getItems().size();
        for (CartItem guestItem : List.copyOf(guestCart.getItems())) {
            CartItem existing = cartItemRepository
                    .findByCartIdAndProductId(userCart.getId(), guestItem.getProductId())
                    .orElse(null);
            if (existing != null) {
                existing.updateQuantity(existing.getQuantity() + guestItem.getQuantity());
                cartItemRepository.save(existing);
            } else {
                CartItem moved = CartItem.builder()
                        .cart(userCart)
                        .productId(guestItem.getProductId())
                        .productName(guestItem.getProductName())
                        .productSku(guestItem.getProductSku())
                        .productImageUrl(guestItem.getProductImageUrl())
                        .priceSnapshot(guestItem.getPriceSnapshot())
                        .quantity(guestItem.getQuantity())
                        .build();
                moved.calculateSubtotal();
                userCart.addItem(moved);
                cartItemRepository.save(moved);
            }
        }

        userCart.setExpiresAt(Instant.now().plus(CART_EXPIRATION_DAYS, ChronoUnit.DAYS));
        userCart.recalculateTotals();
        Cart savedCart = cartRepository.save(userCart);

        // The guest cart has been absorbed; delete it (orphanRemoval clears its
        // items) so it can never be re-merged or checked out as a stale cart.
        cartRepository.delete(guestCart);

        log.info("Merged guest cart {} ({} items) into user cart {} for user {}",
                guestCart.getId(), mergedItems, savedCart.getId(), userId);
        return toCartResponse(savedCart);
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

    /**
     * Populate {@link Cart#getUserEmail()} from user-service if not already
     * resolved. No-op when the email is present; a failed lookup leaves it
     * null so the operation never blocks on user-service availability.
     */
    private void ensureUserEmail(Cart cart) {
        if (cart.getUserEmail() != null && !cart.getUserEmail().isBlank()) {
            return;
        }
        resolveUserEmail(cart.getUserId()).ifPresent(cart::setUserEmail);
    }

    private Optional<String> resolveUserEmail(String userId) {
        try {
            UserContactDto contact = userServiceClient.getUserByAuth0Id(userId);
            if (contact != null && contact.getEmail() != null && !contact.getEmail().isBlank()) {
                return Optional.of(contact.getEmail());
            }
            log.warn("user-service returned no email for user {}", userId);
        } catch (Exception e) {
            log.warn("Failed to resolve email for user {} from user-service: {}", userId, e.getMessage());
        }
        return Optional.empty();
    }

    private ProductDto getProductOrThrow(String productId) {
        try {
            ProductDto product = productServiceGateway.getProductById(productId);
            if (product == null) {
                log.error("Product not found: {}", productId);
                throw new ProductNotAvailableException("Product not found: " + productId);
            }
            return product;
        } catch (ProductNotAvailableException e) {
            throw e;
        } catch (ProductServiceUnavailableException e) {
            // product-service is down/brownout — do NOT degrade to a 400
            // "not available"; fail fast so the API returns a retryable 503.
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch product: {}", productId, e);
            throw new ProductNotAvailableException("Product not available: " + productId);
        }
    }

    private void validateProductAvailability(ProductDto product, int requestedQuantity) {
        if (product.getPrice() == null) {
            throw new ProductNotAvailableException("Price information unavailable for product: " + product.getId());
        }
        if (product.getStockQuantity() == null) {
            throw new ProductNotAvailableException("Stock information unavailable for product: " + product.getId());
        }
        if (product.getActive() == null || !product.getActive()) {
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
