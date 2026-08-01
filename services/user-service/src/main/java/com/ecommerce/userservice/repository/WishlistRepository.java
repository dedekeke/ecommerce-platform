package com.ecommerce.userservice.repository;

import com.ecommerce.userservice.domain.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Wishlist Repository
 */
@Repository
public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    /**
     * Returns the wishlist entries for a given user, newest first.
     */
    List<Wishlist> findByUserIdOrderByAddedAtDesc(Long userId);

    Optional<Wishlist> findByUserIdAndProductId(Long userId, String productId);

    boolean existsByUserIdAndProductId(Long userId, String productId);

    void deleteByUserIdAndProductId(Long userId, String productId);
}
