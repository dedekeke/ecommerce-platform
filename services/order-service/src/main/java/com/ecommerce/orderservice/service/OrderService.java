package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.client.PromotionServiceClient;
import com.ecommerce.orderservice.client.dto.DiscountResult;
import com.ecommerce.orderservice.client.dto.PromotionValidationRequest;
import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.event.OrderEventPublisher;
import com.ecommerce.orderservice.exception.InvalidOrderStatusTransitionException;
import com.ecommerce.orderservice.exception.OrderNotFoundException;
import com.ecommerce.orderservice.repository.OrderRepository;
import com.ecommerce.orderservice.shipping.ShippingProvider;
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
    private final PromotionServiceClient promotionServiceClient;
    private final OrderEventPublisher orderEventPublisher;
    private final ShippingProvider shippingProvider;

    @Value("${order.tax.rate:0.08}")
    private Double taxRate;

    @Value("${order.shipping.base-cost:5.99}")
    private Double shippingBaseCost;

    @Value("${order.shipping.free-threshold:50.00}")
    private Double freeShippingThreshold;

    /**
     * Create a new authenticated (non-guest) order.
     */
    @Transactional
    public Order createOrder(
        String userId,
        List<OrderItem> items,
        Address shippingAddress,
        String promotionCode
    ) {
        return createOrder(userId, items, shippingAddress, promotionCode, null);
    }

    /**
     * Create a new order, optionally flagged as a guest order in the SAME
     * transaction as the INSERT.
     *
     * <p>{@code guestEmail} non-null marks this as a guest order and persists the
     * claim key atomically with order creation. This is intentional: doing it as
     * one transaction (rather than a follow-up {@code markAsGuestOrder} call)
     * means a guest order can NEVER be committed unflagged — there is no window
     * in which the row exists without its {@code guest_order}/{@code guest_email}
     * markers, so the later account-claim lookup can always find it.</p>
     */
    @Transactional
    public Order createOrder(
        String userId,
        List<OrderItem> items,
        Address shippingAddress,
        String promotionCode,
        String guestEmail
    ) {
        log.info("Creating order for user: {}", userId);

        // Generate order number
        String orderNumber = orderNumberGenerator.generateOrderNumber();

        // Calculate subtotal
        BigDecimal subtotal = items.stream()
            .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Validate and apply promotion if provided
        BigDecimal discountAmount = null;
        String validPromotionCode = null;

        if (promotionCode != null && !promotionCode.trim().isEmpty()) {
            log.info("Validating promotion code: {}", promotionCode);

            PromotionValidationRequest promotionRequest = PromotionValidationRequest.builder()
                .code(promotionCode)
                .purchaseAmount(subtotal)
                .build();

            DiscountResult discountResult = promotionServiceClient.validatePromotion(promotionRequest);

            if (discountResult.isValid()) {
                discountAmount = discountResult.getDiscountAmount();
                validPromotionCode = promotionCode;
                log.info("Promotion applied: {} - Discount: {}", promotionCode, discountAmount);
            } else {
                log.warn("Promotion validation failed: {} - {}", promotionCode, discountResult.getMessage());
                // Order will be created without the promotion
            }
        }

        // Apply loyalty tier discount on the post-promotion subtotal. The
        // percent is sourced from promotion-service and degrades to zero when
        // that service is unavailable, so checkout never blocks on it.
        BigDecimal postPromotionSubtotal = discountAmount != null
            ? subtotal.subtract(discountAmount).max(BigDecimal.ZERO)
            : subtotal;
        BigDecimal loyaltyDiscount = calculateLoyaltyDiscount(userId, postPromotionSubtotal);

        // CHOSEN BEHAVIOR: tax is levied on the post-discount taxable amount,
        // i.e. (postPromotionSubtotal - loyaltyDiscount) floored at 0, NOT on
        // the gross subtotal. Promo + loyalty discounts therefore reduce the
        // taxable base, so the customer is not taxed on money they never paid.
        // (Tax-on-discounted-subtotal is a jurisdiction-dependent policy —
        // flagged for product confirmation.)
        BigDecimal taxableAmount = postPromotionSubtotal.subtract(loyaltyDiscount)
            .max(BigDecimal.ZERO);
        BigDecimal tax = taxableAmount.multiply(BigDecimal.valueOf(taxRate))
            .setScale(2, RoundingMode.HALF_UP);

        // Shipping is assessed on the gross subtotal (free-shipping threshold
        // is a merchandising decision, intentionally independent of discounts).
        BigDecimal shippingCost = calculateShippingCost(subtotal);

        BigDecimal discountTotal = (discountAmount != null ? discountAmount : BigDecimal.ZERO)
            .add(loyaltyDiscount);
        BigDecimal total = subtotal.subtract(discountTotal).max(BigDecimal.ZERO)
            .add(tax).add(shippingCost);

        // Build order
        Order order = Order.builder()
            .orderNumber(orderNumber)
            .userId(userId)
            .subtotal(subtotal)
            .tax(tax)
            .shippingCost(shippingCost)
            .total(total)
            .status(OrderStatus.PENDING)
            .shippingAddress(shippingAddress)
            .promotionCode(validPromotionCode)
            .discountAmount(discountAmount)
            .loyaltyDiscount(loyaltyDiscount.signum() > 0 ? loyaltyDiscount : null)
            .guestOrder(guestEmail != null)
            .guestEmail(guestEmail)
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
     * Mark an order as shipped: validate the transition to SHIPPED, resolve the
     * authoritative carrier + tracking via the {@link ShippingProvider} seam,
     * persist the shipment (carrier / tracking / shippedAt), and record the
     * ORDER_SHIPPED event in the SAME transaction (outbox) so the customer
     * shipping notification can never disagree with the persisted state.
     *
     * <p>The transition guard rejects any non-shippable source state (e.g.
     * PENDING, an already-SHIPPED/DELIVERED order) with
     * {@link InvalidOrderStatusTransitionException}. Valid sources: CONFIRMED,
     * PROCESSING.</p>
     */
    @Transactional
    public Order markOrderShipped(String orderId, String carrier, String trackingNumber) {
        log.info("Marking order {} shipped (carrier={})", orderId, carrier);

        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        if (!order.getStatus().canTransitionTo(OrderStatus.SHIPPED)) {
            throw new InvalidOrderStatusTransitionException(order.getStatus(), OrderStatus.SHIPPED);
        }

        ShippingProvider.Shipment shipment = shippingProvider.createShipment(
            new ShippingProvider.ShipmentRequest(
                order.getId(), order.getOrderNumber(), carrier, trackingNumber));

        order.markShipped(shipment.carrier(), shipment.trackingNumber());
        Order shipped = orderRepository.save(order);

        orderEventPublisher.publishOrderShippedEvent(shipped);
        log.info("Order {} marked shipped — carrier={}, tracking={}",
            shipped.getOrderNumber(), shipped.getCarrier(), shipped.getTrackingNumber());
        return shipped;
    }

    /**
     * Mark an order as delivered: validate the transition (SHIPPED -> DELIVERED)
     * and stamp {@code deliveredAt}. No event is emitted — delivery is a terminal
     * state change with no downstream notification in this foundation.
     */
    @Transactional
    public Order markOrderDelivered(String orderId) {
        log.info("Marking order {} delivered", orderId);

        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        if (!order.getStatus().canTransitionTo(OrderStatus.DELIVERED)) {
            throw new InvalidOrderStatusTransitionException(order.getStatus(), OrderStatus.DELIVERED);
        }

        order.markDelivered();
        Order delivered = orderRepository.save(order);
        log.info("Order {} marked delivered", delivered.getOrderNumber());
        return delivered;
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
     * Finalize a successful checkout: persist the PaymentIntent id + client
     * secret AND record ORDER_CREATED in a SINGLE transaction. Because the
     * outbox row commits atomically with the order mutation, the confirmation
     * event can never disagree with the persisted state. The client secret is
     * stored so an idempotent replay can re-serve it to the owning session
     * (payment-service exposes no lookup-by-intent RPC).
     */
    @Transactional
    public Order finalizeSuccessfulOrder(
        String orderId,
        String paymentIntentId,
        String paymentClientSecret,
        String userEmail,
        String userName
    ) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        order.setPaymentIntentId(paymentIntentId);
        order.setPaymentClientSecret(paymentClientSecret);
        Order saved = orderRepository.save(order);

        orderEventPublisher.publishOrderCreatedEvent(saved, userEmail, userName);
        log.info("Finalized order {} with payment intent {}", order.getOrderNumber(), paymentIntentId);
        return saved;
    }

    /**
     * Backfill/reconcile helper: mark an existing order as a guest order and
     * persist the claim key (the normalized email the guest checked out under).
     *
     * <p>The guest-checkout path no longer relies on this — the flag is now set
     * atomically inside {@link #createOrder(String, List, Address, String, String)}
     * so a guest order can never be committed unflagged. This method is retained
     * as an idempotent reconcile/admin utility (e.g. to backfill pre-existing
     * rows); safe to re-apply on an already-flagged order.</p>
     */
    @Transactional
    public Order markAsGuestOrder(String orderId, String normalizedEmail) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        order.setGuestOrder(true);
        order.setGuestEmail(normalizedEmail);
        Order saved = orderRepository.save(order);
        log.info("Marked order {} as guest order (claim key persisted)", order.getOrderNumber());
        return saved;
    }

    /**
     * Claim hook for account linking: relink every guest order placed under
     * {@code normalizedEmail} to a now-registered user. Invoked by the
     * account-linking flow once the email has been VERIFIED (the caller owns
     * that gate — verification is what makes the claim safe; this method does not
     * re-verify). The {@code guestEmail} marker is retained for audit so the
     * order's provenance stays visible after the relink.
     *
     * <p>Returns the number of orders relinked. Idempotent: a second call finds
     * no remaining guest orders under that email and relinks nothing.</p>
     */
    @Transactional
    public int claimGuestOrders(String normalizedEmail, String newUserId) {
        List<Order> guestOrders = orderRepository.findByGuestEmailAndGuestOrderTrue(normalizedEmail);
        guestOrders.forEach(order -> order.setUserId(newUserId));
        orderRepository.saveAll(guestOrders);
        log.info("Claimed {} guest order(s) for user {}", guestOrders.size(), newUserId);
        return guestOrders.size();
    }

    /**
     * Compensating cancel for the order-creation saga: transition the order to
     * CANCELLED AND record ORDER_CANCELLED in the SAME transaction. This commits
     * independently of the (non-transactional) saga orchestration, so a failed
     * checkout leaves the DB and the emitted event in agreement — never a
     * PENDING row alongside an ORDER_CANCELLED event. Idempotent: an
     * already-cancelled order is a no-op.
     */
    @Transactional
    public void compensateCancelOrder(String orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Compensation cancel skipped — order {} not found", orderId);
            return;
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }
        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            log.warn("Compensation cancel skipped — order {} in non-cancellable status {}",
                orderId, order.getStatus());
            return;
        }
        order.updateStatus(OrderStatus.CANCELLED);
        Order cancelled = orderRepository.save(order);
        orderEventPublisher.publishOrderCancelledEvent(cancelled);
        log.info("Compensation cancelled order {}", cancelled.getOrderNumber());
    }

    /**
     * Advance a PENDING order to CONFIRMED ("paid") in response to a settled
     * payment, recording ORDER_UPDATED in the SAME transaction (outbox) so the
     * state change and its event commit atomically.
     *
     * <p>Idempotent + convergent — safe to call for an at-least-once redelivery
     * or after the client-confirm path already advanced the order:
     * <ul>
     *   <li>{@code PENDING} &rarr; {@code CONFIRMED} (+ ORDER_UPDATED): the real
     *       transition.</li>
     *   <li>{@code CONFIRMED}..{@code DELIVERED}: no-op — already paid/advanced;
     *       we never re-emit or double-apply.</li>
     *   <li>{@code CANCELLED}/{@code REFUNDED}: no-op + ERROR log — a payment
     *       settled against a dead order is an ops signal, never a silent
     *       resurrection.</li>
     * </ul>
     *
     * @return {@code true} iff this call performed the PENDING&rarr;CONFIRMED transition
     */
    @Transactional
    public boolean confirmOrderPaid(String orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("payment.completed for unknown order {} — ignoring", orderId);
            return false;
        }
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.PENDING) {
            order.updateStatus(OrderStatus.CONFIRMED);
            Order saved = orderRepository.save(order);
            orderEventPublisher.publishOrderUpdatedEvent(saved);
            log.info("Order {} confirmed (PAID) from settled payment", saved.getOrderNumber());
            return true;
        }
        if (status == OrderStatus.CANCELLED || status == OrderStatus.REFUNDED) {
            log.error("payment.completed for order {} in terminal state {} — funds may be "
                + "captured on a dead order; NOT resurrecting", orderId, status);
            return false;
        }
        log.info("payment.completed for order {} already in {} — no-op (idempotent)", orderId, status);
        return false;
    }

    /**
     * Handle a settled-as-FAILED payment: cancel a still-PENDING order via the
     * durable cancel + ORDER_CANCELLED path ({@link #compensateCancelOrder}) so
     * the caller can release the inventory reservation.
     *
     * <p>Precedence (defines who wins on out-of-order / conflicting events):
     * <ul>
     *   <li>{@code PENDING} &rarr; {@code CANCELLED} via compensateCancelOrder;
     *       returns {@code true} (reservation release needed).</li>
     *   <li>{@code CONFIRMED}..{@code DELIVERED}: NO-OP — a late/stale
     *       payment.failed must never downgrade an order whose payment already
     *       succeeded (a completed payment wins over a subsequent failure).</li>
     *   <li>{@code CANCELLED}/{@code REFUNDED}: no-op — already terminal, converges.</li>
     * </ul>
     *
     * <p>Note: compensateCancelOrder alone would happily cancel a CONFIRMED order
     * (CONFIRMED is a cancellable state), so the PENDING gate here is what
     * enforces the no-downgrade rule before reusing it.
     *
     * @return {@code true} iff this call cancelled the order (reservation release needed)
     */
    @Transactional
    public boolean failOrderPayment(String orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("payment.failed for unknown order {} — ignoring", orderId);
            return false;
        }
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.PENDING) {
            compensateCancelOrder(orderId);
            return true;
        }
        if (status == OrderStatus.CANCELLED) {
            log.info("payment.failed for already-CANCELLED order {} — no-op (idempotent)", orderId);
            return false;
        }
        log.warn("payment.failed for order {} in state {} — payment already settled; "
            + "NOT downgrading", orderId, status);
        return false;
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
     * Resolve the loyalty tier discount as an absolute amount off the given
     * base. Returns {@link BigDecimal#ZERO} when no discount applies (unknown
     * user, no tier, or promotion-service unavailable).
     */
    private BigDecimal calculateLoyaltyDiscount(String userId, BigDecimal base) {
        if (base == null || base.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal percent = promotionServiceClient.getLoyaltyDiscountPercent(userId);
        if (percent == null || percent.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal discount = base.multiply(percent)
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        log.info("Applying loyalty discount {} ({}%) for user {}", discount, percent, userId);
        return discount;
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
