package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.exception.InvalidOrderStatusTransitionException;
import com.ecommerce.orderservice.exception.OrderNotFoundException;
import com.ecommerce.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for order management and business logic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderNumberGeneratorService orderNumberGenerator;

    @Value("${order.tax.rate:0.08}")
    private Double taxRate;

    @Value("${order.shipping.base-cost:5.99}")
    private Double shippingBaseCost;

    @Value("${order.shipping.free-threshold:50.00}")
    private Double freeShippingThreshold;

    /**
     * Create a new order
     */
    @Transactional
    public Order createOrder(
        String userId,
        List<OrderItem> items,
        Address shippingAddress,
        String promotionCode
    ) {
        log.info("Creating order for user: {}", userId);

        // Generate order number
        String orderNumber = orderNumberGenerator.generateOrderNumber();

        // Calculate subtotal
        BigDecimal subtotal = items.stream()
            .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate tax
        BigDecimal tax = subtotal.multiply(BigDecimal.valueOf(taxRate))
            .setScale(2, RoundingMode.HALF_UP);

        // Calculate shipping cost
        BigDecimal shippingCost = calculateShippingCost(subtotal);

        // Build order
        Order order = Order.builder()
            .orderNumber(orderNumber)
            .userId(userId)
            .subtotal(subtotal)
            .tax(tax)
            .shippingCost(shippingCost)
            .total(subtotal.add(tax).add(shippingCost))
            .status(OrderStatus.PENDING)
            .shippingAddress(shippingAddress)
            .promotionCode(promotionCode)
            .build();

        // Add items to order
        items.forEach(item -> {
            item.setOrder(order);
            order.addItem(item);
        });

        // Save order
        Order savedOrder = orderRepository.save(order);

        log.info("Order created successfully: {} for user: {}", orderNumber, userId);
        return savedOrder;
    }

    /**
     * Get order by ID
     */
    @Transactional(readOnly = true)
    public Order getOrder(String orderId, String userId) {
        log.debug("Fetching order: {} for user: {}", orderId, userId);

        return orderRepository.findById(orderId)
            .filter(order -> order.getUserId().equals(userId))
            .orElseThrow(() -> new OrderNotFoundException(orderId, userId));
    }

    /**
     * Get order by order number
     */
    @Transactional(readOnly = true)
    public Order getOrderByNumber(String orderNumber) {
        log.debug("Fetching order by number: {}", orderNumber);

        return orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderNumber));
    }

    /**
     * Get all orders for a user
     */
    @Transactional(readOnly = true)
    public Page<Order> getUserOrders(String userId, Pageable pageable) {
        log.debug("Fetching orders for user: {}", userId);
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * Get orders by user and status
     */
    @Transactional(readOnly = true)
    public Page<Order> getUserOrdersByStatus(
        String userId,
        OrderStatus status,
        Pageable pageable
    ) {
        log.debug("Fetching {} orders for user: {}", status, userId);
        return orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageable);
    }

    /**
     * Update order status with state machine validation
     */
    @Transactional
    public Order updateOrderStatus(String orderId, OrderStatus newStatus) {
        log.info("Updating order {} status to {}", orderId, newStatus);

        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        // Validate state transition
        if (!order.getStatus().canTransitionTo(newStatus)) {
            throw new InvalidOrderStatusTransitionException(order.getStatus(), newStatus);
        }

        order.updateStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);

        log.info("Order {} status updated from {} to {}",
            orderId, order.getStatus(), newStatus);

        return updatedOrder;
    }

    /**
     * Cancel an order
     */
    @Transactional
    public Order cancelOrder(String orderId, String userId, String reason) {
        log.info("Cancelling order {} for user: {}", orderId, userId);

        Order order = getOrder(orderId, userId);

        if (!order.canBeCancelled()) {
            throw new InvalidOrderStatusTransitionException(
                "Order cannot be cancelled in current status: " + order.getStatus()
            );
        }

        order.updateStatus(OrderStatus.CANCELLED);
        Order cancelledOrder = orderRepository.save(order);

        log.info("Order {} cancelled successfully. Reason: {}", orderId, reason);
        return cancelledOrder;
    }

    /**
     * Set payment intent ID for an order
     */
    @Transactional
    public Order setPaymentIntent(String orderId, String paymentIntentId) {
        log.info("Setting payment intent {} for order {}", paymentIntentId, orderId);

        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        order.setPaymentIntentId(paymentIntentId);
        return orderRepository.save(order);
    }

    /**
     * Get order by payment intent ID
     */
    @Transactional(readOnly = true)
    public Order getOrderByPaymentIntentId(String paymentIntentId) {
        return orderRepository.findByPaymentIntentId(paymentIntentId)
            .orElseThrow(() -> new OrderNotFoundException(
                "Order not found for payment intent: " + paymentIntentId
            ));
    }

    /**
     * Find abandoned orders
     */
    @Transactional(readOnly = true)
    public List<Order> findAbandonedOrders(int hoursAgo) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(hoursAgo);
        return orderRepository.findAbandonedOrders(OrderStatus.PENDING, cutoffTime);
    }

    /**
     * Apply discount to an order
     */
    @Transactional
    public Order applyDiscount(String orderId, BigDecimal discountAmount) {
        log.info("Applying discount of {} to order {}", discountAmount, orderId);

        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        order.setDiscountAmount(discountAmount);
        order.calculateTotals(); // Recalculate totals with discount

        return orderRepository.save(order);
    }

    /**
     * Calculate shipping cost based on subtotal
     */
    private BigDecimal calculateShippingCost(BigDecimal subtotal) {
        if (subtotal.compareTo(BigDecimal.valueOf(freeShippingThreshold)) >= 0) {
            return BigDecimal.ZERO; // Free shipping
        }
        return BigDecimal.valueOf(shippingBaseCost).setScale(2, RoundingMode.HALF_UP);
    }
}
