package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.dto.CheckoutRequest;
import com.ecommerce.orderservice.dto.CheckoutResponse;
import com.ecommerce.orderservice.dto.GuestCheckoutRequest;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.dto.PageResponse;
import com.ecommerce.orderservice.exception.GuestCheckoutAuthenticationException;
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
        // IDOR guard: the order number is guessable, so verify ownership after lookup.
        userIdentityResolver.assertCanActFor(order.getUserId(), jwt);
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
