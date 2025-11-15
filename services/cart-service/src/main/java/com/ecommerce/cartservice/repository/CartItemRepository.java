package com.ecommerce.cartservice.repository;

import com.ecommerce.cartservice.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * CartItem Repository
 *
 * Data access layer for CartItem entity.
 */
@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /**
     * Find all items in a cart
     */
    List<CartItem> findByCartId(Long cartId);

    /**
     * Find a specific item in a cart by product ID
     */
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    /**
     * Delete all items in a cart
     */
    void deleteByCartId(Long cartId);

    /**
     * Count items in a cart
     */
    long countByCartId(Long cartId);

    /**
     * Find all cart items containing a specific product
     * (Useful for price updates or product deletion)
     */
    @Query("SELECT ci FROM CartItem ci WHERE ci.productId = :productId")
    List<CartItem> findByProductId(@Param("productId") Long productId);
}
