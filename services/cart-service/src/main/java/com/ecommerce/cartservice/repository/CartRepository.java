package com.ecommerce.cartservice.repository;

import com.ecommerce.cartservice.domain.Cart;
import com.ecommerce.cartservice.domain.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUserIdAndStatus(String userId, CartStatus status);

    List<Cart> findByUserId(String userId);

    @Query("SELECT c FROM Cart c WHERE c.expiresAt < :now AND c.status = :status")
    List<Cart> findExpiredCarts(@Param("now") Instant now, @Param("status") CartStatus status);

    @Query("SELECT c FROM Cart c WHERE c.updatedAt < :threshold AND c.status = :status")
    List<Cart> findAbandonedCarts(@Param("threshold") Instant threshold, @Param("status") CartStatus status);

    void deleteByUserIdAndStatus(String userId, CartStatus status);

    boolean existsByUserIdAndStatus(String userId, CartStatus status);
}
