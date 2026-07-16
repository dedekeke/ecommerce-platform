package com.ecommerce.orderservice.repository;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Order entity
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    /**
     * Find order by order number
     */
    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * Find all orders for a user
     */
    Page<Order> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * Find orders by user and status
     */
    Page<Order> findByUserIdAndStatusOrderByCreatedAtDesc(
        String userId,
        OrderStatus status,
        Pageable pageable
    );

    /**
     * Find orders by status
     */
    List<Order> findByStatus(OrderStatus status);

    /**
     * Find orders by status, paginated. Backs the admin order list's optional
     * status filter; sort order is supplied via the {@link Pageable}.
     */
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    /**
     * Find orders created between dates
     */
    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate")
    List<Order> findOrdersByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Count orders by status for a user
     */
    long countByUserIdAndStatus(String userId, OrderStatus status);

    /**
     * Find orders by payment intent ID
     */
    Optional<Order> findByPaymentIntentId(String paymentIntentId);

    /**
     * Check if order exists for user
     */
    boolean existsByIdAndUserId(String id, String userId);

    /**
     * Find the latest order number for generating next order number
     */
    @Query("SELECT o.orderNumber FROM Order o ORDER BY o.createdAt DESC LIMIT 1")
    Optional<String> findLatestOrderNumber();

    /**
     * Find guest orders placed under a given email (the claim key). Used to
     * relink a guest's orders to their real account once they register/verify
     * that address. Email is stored already-normalized (trim + lowercase).
     */
    List<Order> findByGuestEmailAndGuestOrderTrue(String guestEmail);

    /**
     * Find abandoned orders (PENDING for more than specified hours)
     */
    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.createdAt < :cutoffTime")
    List<Order> findAbandonedOrders(
        @Param("status") OrderStatus status,
        @Param("cutoffTime") LocalDateTime cutoffTime
    );
}
