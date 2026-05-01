package com.ecommerce.orderservice.subscription;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for §3.6 subscription / recurring orders.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST   /api/subscriptions                         — create</li>
 *   <li>GET    /api/subscriptions/user/{userId}           — list per user</li>
 *   <li>PUT    /api/subscriptions/{id}/pause              — transition to PAUSED</li>
 *   <li>PUT    /api/subscriptions/{id}/resume             — transition to ACTIVE</li>
 *   <li>PUT    /api/subscriptions/{id}/cancel             — transition to CANCELLED</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscriptions", description = "Recurring order subscriptions (§3.6)")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    @Operation(summary = "Create a recurring subscription")
    public ResponseEntity<Subscription> create(@Valid @RequestBody SubscriptionDtos.CreateSubscriptionRequest req) {
        log.info("REST: Create subscription for user={} product={}", req.userId(), req.productId());
        return ResponseEntity.status(201).body(subscriptionService.create(req));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "List subscriptions for a user")
    public ResponseEntity<List<Subscription>> listForUser(@PathVariable String userId) {
        return ResponseEntity.ok(subscriptionService.listForUser(userId));
    }

    @PutMapping("/{id}/pause")
    @Operation(summary = "Pause an active subscription")
    public ResponseEntity<Subscription> pause(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.pause(id));
    }

    @PutMapping("/{id}/resume")
    @Operation(summary = "Resume a paused subscription")
    public ResponseEntity<Subscription> resume(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.resume(id));
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel a subscription (terminal)")
    public ResponseEntity<Subscription> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.cancel(id));
    }
}
