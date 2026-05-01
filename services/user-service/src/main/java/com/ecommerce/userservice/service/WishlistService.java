package com.ecommerce.userservice.service;

import com.ecommerce.userservice.domain.User;
import com.ecommerce.userservice.domain.Wishlist;
import com.ecommerce.userservice.exception.UnauthorizedAccessException;
import com.ecommerce.userservice.exception.UserNotFoundException;
import com.ecommerce.userservice.exception.WishlistItemNotFoundException;
import com.ecommerce.userservice.repository.UserRepository;
import com.ecommerce.userservice.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Wishlist Service
 *
 * <p>Authorization model: every public method receives both a path-level
 * {@code userId} (from the request URL) and the calling user's Auth0 subject
 * ({@code callerAuth0Id}, taken from the JWT). The service resolves the Auth0
 * subject to a user id and rejects the call with {@link UnauthorizedAccessException}
 * unless it matches the path id. This prevents user A from reading or
 * mutating user B's wishlist via path manipulation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final UserRepository userRepository;

    /**
     * Get all wishlist entries for the user, newest first.
     */
    @Transactional(readOnly = true)
    public List<Wishlist> getWishlist(Long userId, String callerAuth0Id) {
        authorizeOwner(userId, callerAuth0Id);
        return wishlistRepository.findByUserIdOrderByAddedAtDesc(userId);
    }

    /**
     * Add a product to the user's wishlist. Idempotent: returns the existing
     * entry if the product is already on the wishlist.
     */
    @Transactional
    public Wishlist addItem(Long userId, String productId, String callerAuth0Id) {
        authorizeOwner(userId, callerAuth0Id);

        return wishlistRepository.findByUserIdAndProductId(userId, productId)
                .orElseGet(() -> {
                    Wishlist entry = Wishlist.builder()
                            .userId(userId)
                            .productId(productId)
                            .addedAt(Instant.now())
                            .build();
                    Wishlist saved = wishlistRepository.save(entry);
                    log.info("Added product {} to wishlist for user {}", productId, userId);
                    return saved;
                });
    }

    /**
     * Remove a product from the user's wishlist.
     *
     * @throws WishlistItemNotFoundException if the product is not on the wishlist.
     */
    @Transactional
    public void removeItem(Long userId, String productId, String callerAuth0Id) {
        authorizeOwner(userId, callerAuth0Id);

        if (!wishlistRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new WishlistItemNotFoundException(
                    "Wishlist item not found for userId=" + userId + ", productId=" + productId);
        }
        wishlistRepository.deleteByUserIdAndProductId(userId, productId);
        log.info("Removed product {} from wishlist for user {}", productId, userId);
    }

    /**
     * Authorize that the caller's Auth0 subject corresponds to the path user id.
     * Throws {@link UnauthorizedAccessException} on mismatch.
     */
    private void authorizeOwner(Long pathUserId, String callerAuth0Id) {
        if (callerAuth0Id == null || callerAuth0Id.isBlank()) {
            throw new UnauthorizedAccessException("Authentication required");
        }

        User caller = userRepository.findByAuth0Id(callerAuth0Id)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found for auth0Id=" + callerAuth0Id));

        if (!caller.getId().equals(pathUserId)) {
            log.warn("Unauthorized wishlist access: caller userId={} attempted to access userId={}",
                    caller.getId(), pathUserId);
            throw new UnauthorizedAccessException(
                    "You are not authorized to access this wishlist");
        }
    }
}
