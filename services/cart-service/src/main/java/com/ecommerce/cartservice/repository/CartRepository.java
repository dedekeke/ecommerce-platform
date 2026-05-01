package com.ecommerce.cartservice.repository;

import com.ecommerce.cartservice.domain.Cart;
import com.ecommerce.cartservice.domain.CartStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    /**
     * Cart hot path: every read in {@code CartService} touches
     * {@code cart.getItems()}, so we LEFT JOIN them in the same query
     * via {@code @EntityGraph}. Without this hint, the lazy items
     * collection forces a second SELECT per cart on first access.
     *
     * <p>{@code @EntityGraph} is preferred over {@code JOIN FETCH} here
     * because the method has no Pageable — the safety reason for choosing
     * EntityGraph (no in-memory paging surprises) doesn't bite, but we
     * keep the convention consistent with the paging methods that do.
     *
     * <p>See docs/N1_AUDIT.md for the audit findings.
     */
    @EntityGraph(attributePaths = "items")
    Optional<Cart> findByUserIdAndStatus(String userId, CartStatus status);

    @EntityGraph(attributePaths = "items")
    List<Cart> findByUserId(String userId);

    /**
     * Used by {@code CartCleanupScheduledJob} which iterates carts and
     * touches their items to publish events — N+1 hot spot pre-fix.
     */
    @EntityGraph(attributePaths = "items")
    @Query("SELECT c FROM Cart c WHERE c.expiresAt < :now AND c.status = :status")
    List<Cart> findExpiredCarts(@Param("now") Instant now, @Param("status") CartStatus status);

    @EntityGraph(attributePaths = "items")
    @Query("SELECT c FROM Cart c WHERE c.updatedAt < :threshold AND c.status = :status")
    List<Cart> findAbandonedCarts(@Param("threshold") Instant threshold, @Param("status") CartStatus status);

    /**
     * Find ACTIVE carts that look abandoned and are eligible for a recovery
     * email: idle longer than {@code idleThreshold}, contain at least one
     * line item, and either never reminded or last reminded before
     * {@code reminderCutoff} (7-day cool-off).
     *
     * <p>{@code AbandonedCartScanner.toEvent} iterates {@code cart.getItems()}
     * for each row in the result — pre-fix this was a textbook N+1 hot
     * spot. The {@code @EntityGraph} fans out a single LEFT JOIN load.
     */
    @EntityGraph(attributePaths = "items")
    @Query("""
            SELECT c FROM Cart c
             WHERE c.status = :status
               AND c.updatedAt < :idleThreshold
               AND SIZE(c.items) > 0
               AND (c.lastAbandonmentReminderAt IS NULL OR c.lastAbandonmentReminderAt < :reminderCutoff)
            """)
    List<Cart> findCartsEligibleForAbandonmentReminder(
            @Param("status") CartStatus status,
            @Param("idleThreshold") Instant idleThreshold,
            @Param("reminderCutoff") Instant reminderCutoff);

    void deleteByUserIdAndStatus(String userId, CartStatus status);

    boolean existsByUserIdAndStatus(String userId, CartStatus status);
}
