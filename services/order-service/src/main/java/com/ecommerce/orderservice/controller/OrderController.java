package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.dto.AdminOrderResponse;
import com.ecommerce.orderservice.dto.CheckoutRequest;
import com.ecommerce.orderservice.dto.CheckoutResponse;
import com.ecommerce.orderservice.dto.GuestCheckoutRequest;
import com.ecommerce.orderservice.dto.MarkShippedRequest;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.dto.PageResponse;
import com.ecommerce.orderservice.exception.GuestCheckoutAuthenticationException;
import com.ecommerce.orderservice.exception.OrderNotFoundException;
import com.ecommerce.orderservice.mapper.AdminOrderMapper;
import com.ecommerce.orderservice.security.UserIdentityResolver;
import com.ecommerce.orderservice.service.CheckoutService;
import com.ecommerce.orderservice.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * REST API controller for Order operations
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Order Management", description = "APIs for managing orders")
public class OrderController {

    private final OrderService orderService;
    private final CheckoutService checkoutService;
    private final UserIdentityResolver userIdentityResolver;
    private final AdminOrderMapper adminOrderMapper;

    @PostMapping
    @Operation(summary = "Create an order from the authenticated user's cart via the checkout saga")
    public ResponseEntity<CheckoutResponse> createOrder(
        @Valid @RequestBody CheckoutRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @AuthenticationPrincipal Jwt jwt
    ) {
        // JWT subject is authoritative; a body userId is only a correlation hint.
        String userId = userIdentityResolver.resolveUserId(request.userId(), jwt);
        log.info("REST: Create order for user {} (idempotencyKey present: {})",
            userId, idempotencyKey != null && !idempotencyKey.isBlank());

        CheckoutService.Outcome outcome = checkoutService.checkout(userId, idempotencyKey, request);
        HttpStatus status = outcome.replay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(outcome.response());
    }

    @PostMapping("/guest")
    @Operation(summary = "Create an order as an unauthenticated guest",
        description = "Guest checkout: no JWT allowed. The owning identity is derived server-side "
            + "from the validated email — the client cannot assert who it is. A request carrying an "
            + "Authorization credential is rejected with 400: authenticated callers must use "
            + "POST /api/orders. Same Idempotency-Key and clientSecret contract as the "
            + "authenticated create.")
    public ResponseEntity<CheckoutResponse> createGuestOrder(
        @Valid @RequestBody GuestCheckoutRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        // An authenticated caller must NOT use the guest path: the order would be
        // owned by an email-derived guest identity, divorced from their account.
        // Reject on the mere presence of an Authorization credential (validity is
        // irrelevant — they should drop the header and use POST /api/orders).
        if (authorization != null && !authorization.isBlank()) {
            throw new GuestCheckoutAuthenticationException(
                "Guest checkout does not accept an Authorization credential; "
                    + "authenticated users must use POST /api/orders");
        }
        // No JWT and no client-supplied identity: the owner is derived from the
        // email inside CheckoutService, so this endpoint cannot be used to place
        // an order on behalf of an authenticated user.
        log.info("REST: Guest create order (idempotencyKey present: {})",
            idempotencyKey != null && !idempotencyKey.isBlank());

        CheckoutService.Outcome outcome = checkoutService.guestCheckout(idempotencyKey, request);
        HttpStatus status = outcome.replay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(outcome.response());
    }

    @GetMapping
    @Operation(summary = "List all orders (admin only)",
        description = "Admin-scoped, paginated order list backing the admin dashboard. Optional "
            + "status filter; results are sorted newest-first (createdAt desc). Returns "
            + "AdminOrderResponse, which — unlike the customer-facing OrderResponse — includes "
            + "the customer identity (userId, guestEmail, guestOrder flag) needed to manage "
            + "orders. Payment secrets are never exposed.")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    public ResponseEntity<PageResponse<AdminOrderResponse>> listOrders(
        @RequestParam(required = false) OrderStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        log.info("REST: Admin list orders (status={}, page={}, size={})", status, page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orders = orderService.getAllOrders(status, pageable);
        return ResponseEntity.ok(PageResponse.from(orders.map(adminOrderMapper::toAdminResponse)));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by ID")
    public ResponseEntity<OrderResponse> getOrder(
        @PathVariable String orderId,
        @RequestHeader(value = "X-User-Id", required = false) String userId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        String resolvedUserId = userIdentityResolver.resolveUserId(userId, jwt);
        log.info("REST: Get order {} for user {}", orderId, resolvedUserId);
        Order order = orderService.getOrder(orderId, resolvedUserId);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @GetMapping("/number/{orderNumber}")
    @Operation(summary = "Get order by order number")
    public ResponseEntity<OrderResponse> getOrderByNumber(
        @PathVariable String orderNumber,
        @AuthenticationPrincipal Jwt jwt
    ) {
        log.info("REST: Get order by number {}", orderNumber);
        Order order = orderService.getOrderByNumber(orderNumber);
        // Opaque-id enumeration guard: the order number is guessable, so a
        // non-admin who is not the owner gets the SAME 404 (identical message)
        // as a genuinely missing order — 404 vs 403 must not reveal existence.
        // Authorization is preserved: another user's data is never returned.
        if (!userIdentityResolver.canAccess(order.getUserId(), jwt)) {
            throw new OrderNotFoundException("Order not found: " + orderNumber);
        }
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get all orders for a user")
    public ResponseEntity<PageResponse<OrderResponse>> getUserOrders(
        @PathVariable String userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @AuthenticationPrincipal Jwt jwt
    ) {
        String resolvedUserId = userIdentityResolver.resolveUserId(userId, jwt);
        log.info("REST: Get orders for user {} (page: {}, size: {})", resolvedUserId, page, size);
        Page<Order> orders = orderService.getUserOrders(resolvedUserId, PageRequest.of(page, size));
        return ResponseEntity.ok(PageResponse.from(orders.map(OrderResponse::from)));
    }

    @PutMapping("/{orderId}/status")
    @Operation(summary = "Update order status (admin only)")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    public ResponseEntity<OrderResponse> updateOrderStatus(
        @PathVariable String orderId,
        @RequestParam OrderStatus status
    ) {
        log.info("REST: Update order {} status to {}", orderId, status);
        Order order = orderService.updateOrderStatus(orderId, status);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @PostMapping("/{orderId}/mark-shipped")
    @Operation(summary = "Mark an order as shipped with carrier + tracking (admin only)",
        description = "Admin-only. Transitions a CONFIRMED/PROCESSING order to SHIPPED, records the "
            + "carrier + tracking number, stamps shippedAt, and emits the shipping notification. "
            + "Invalid source states are rejected with 409.")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    public ResponseEntity<OrderResponse> markShipped(
        @PathVariable String orderId,
        @Valid @RequestBody MarkShippedRequest request
    ) {
        log.info("REST: Mark order {} shipped (carrier={})", orderId, request.carrier());
        Order order = orderService.markOrderShipped(orderId, request.carrier(), request.trackingNumber());
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @PostMapping("/{orderId}/mark-delivered")
    @Operation(summary = "Mark an order as delivered (admin only)",
        description = "Admin-only. Transitions a SHIPPED order to DELIVERED and stamps deliveredAt. "
            + "Invalid source states are rejected with 409.")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    public ResponseEntity<OrderResponse> markDelivered(
        @PathVariable String orderId
    ) {
        log.info("REST: Mark order {} delivered", orderId);
        Order order = orderService.markOrderDelivered(orderId);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel an order")
    public ResponseEntity<OrderResponse> cancelOrder(
        @PathVariable String orderId,
        @RequestHeader(value = "X-User-Id", required = false) String userId,
        @RequestParam(required = false) String reason,
        @AuthenticationPrincipal Jwt jwt
    ) {
        String resolvedUserId = userIdentityResolver.resolveUserId(userId, jwt);
        log.info("REST: Cancel order {} for user {}", orderId, resolvedUserId);
        Order order = orderService.cancelOrder(orderId, resolvedUserId, reason);
        return ResponseEntity.ok(OrderResponse.from(order));
    }
}
