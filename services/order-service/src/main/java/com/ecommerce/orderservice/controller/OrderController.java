package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.dto.PageResponse;
import com.ecommerce.orderservice.security.UserIdentityResolver;
import com.ecommerce.orderservice.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
    private final UserIdentityResolver userIdentityResolver;

    @PostMapping
    @Operation(summary = "Create order from cart")
    public ResponseEntity<Order> createOrder(
        @RequestBody Map<String, Object> request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = userIdentityResolver.resolveUserId((String) request.get("userId"), jwt);
        log.info("REST: Create order for user {}", userId);

        // For now, create a simple order - full implementation would fetch from cart
        Order order = orderService.createOrderFromCart(userId, request);
        return ResponseEntity.status(201).body(order);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by ID")
    public ResponseEntity<Order> getOrder(
        @PathVariable String orderId,
        @RequestHeader(value = "X-User-Id", required = false) String userId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        String resolvedUserId = userIdentityResolver.resolveUserId(userId, jwt);
        log.info("REST: Get order {} for user {}", orderId, resolvedUserId);
        Order order = orderService.getOrder(orderId, resolvedUserId);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/number/{orderNumber}")
    @Operation(summary = "Get order by order number")
    public ResponseEntity<Order> getOrderByNumber(
        @PathVariable String orderNumber,
        @AuthenticationPrincipal Jwt jwt
    ) {
        log.info("REST: Get order by number {}", orderNumber);
        Order order = orderService.getOrderByNumber(orderNumber);
        // IDOR guard: the order number is guessable, so verify ownership after lookup.
        userIdentityResolver.assertCanActFor(order.getUserId(), jwt);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get all orders for a user")
    public ResponseEntity<PageResponse<Order>> getUserOrders(
        @PathVariable String userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @AuthenticationPrincipal Jwt jwt
    ) {
        String resolvedUserId = userIdentityResolver.resolveUserId(userId, jwt);
        log.info("REST: Get orders for user {} (page: {}, size: {})", resolvedUserId, page, size);
        Page<Order> orders = orderService.getUserOrders(resolvedUserId, PageRequest.of(page, size));
        return ResponseEntity.ok(PageResponse.from(orders));
    }

    @PutMapping("/{orderId}/status")
    @Operation(summary = "Update order status (admin only)")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    public ResponseEntity<Order> updateOrderStatus(
        @PathVariable String orderId,
        @RequestParam OrderStatus status
    ) {
        log.info("REST: Update order {} status to {}", orderId, status);
        Order order = orderService.updateOrderStatus(orderId, status);
        return ResponseEntity.ok(order);
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel an order")
    public ResponseEntity<Order> cancelOrder(
        @PathVariable String orderId,
        @RequestHeader(value = "X-User-Id", required = false) String userId,
        @RequestParam(required = false) String reason,
        @AuthenticationPrincipal Jwt jwt
    ) {
        String resolvedUserId = userIdentityResolver.resolveUserId(userId, jwt);
        log.info("REST: Cancel order {} for user {}", orderId, resolvedUserId);
        Order order = orderService.cancelOrder(orderId, resolvedUserId, reason);
        return ResponseEntity.ok(order);
    }
}
