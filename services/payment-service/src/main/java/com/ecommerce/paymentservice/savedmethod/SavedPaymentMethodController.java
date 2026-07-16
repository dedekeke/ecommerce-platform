package com.ecommerce.paymentservice.savedmethod;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for §3.9 saved payment methods.
 *
 * <p>Authentication: every endpoint requires a JWT (resource-server). The
 * authenticated user's {@code sub} claim is the source of truth for
 * ownership; the legacy {@code X-User-Id} header is honored only as a
 * fallback for local dev where {@code security.enabled=false}.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/payments/methods")
@RequiredArgsConstructor
@Tag(name = "Saved Payment Methods", description = "User payment-method vault (§3.9)")
public class SavedPaymentMethodController {

    private final SavedPaymentMethodService service;

    @PostMapping("/setup-intent")
    @Operation(summary = "Start the secure add-a-card flow; returns a Stripe SetupIntent client secret")
    public ResponseEntity<SavedPaymentMethodDtos.SetupIntentResponse> createSetupIntent(
        @AuthenticationPrincipal Jwt jwt,
        @RequestHeader(value = "X-User-Id", required = false) String headerUserId
    ) {
        String userId = resolveUserId(jwt, headerUserId);
        log.info("REST: Create setup intent for user={}", userId);
        var result = service.createSetupIntent(userId);
        return ResponseEntity.ok(
            new SavedPaymentMethodDtos.SetupIntentResponse(result.setupIntentId(), result.clientSecret()));
    }

    @PostMapping("/confirm")
    @Operation(summary = "Persist the card saved via a completed SetupIntent (owner-verified)")
    public ResponseEntity<SavedPaymentMethod> confirm(
        @Valid @RequestBody SavedPaymentMethodDtos.ConfirmRequest req,
        @AuthenticationPrincipal Jwt jwt,
        @RequestHeader(value = "X-User-Id", required = false) String headerUserId
    ) {
        String userId = resolveUserId(jwt, headerUserId);
        log.info("REST: Confirm setup intent {} for user={}", req.setupIntentId(), userId);
        return ResponseEntity.status(201).body(service.confirmSetupIntent(userId, req.setupIntentId()));
    }

    @PostMapping
    @Operation(summary = "Attach a tokenized payment method to the authenticated user")
    public ResponseEntity<SavedPaymentMethod> attach(
        @Valid @RequestBody SavedPaymentMethodDtos.AttachRequest req,
        @AuthenticationPrincipal Jwt jwt,
        @RequestHeader(value = "X-User-Id", required = false) String headerUserId
    ) {
        String userId = resolveUserId(jwt, headerUserId);
        log.info("REST: Attach payment method for user={}", userId);
        return ResponseEntity.status(201).body(service.attach(userId, req.token()));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "List saved payment methods for the given user (must match authenticated user)")
    public ResponseEntity<List<SavedPaymentMethod>> list(
        @PathVariable String userId,
        @AuthenticationPrincipal Jwt jwt,
        @RequestHeader(value = "X-User-Id", required = false) String headerUserId
    ) {
        String authUserId = resolveUserId(jwt, headerUserId);
        if (!authUserId.equals(userId)) {
            throw new SecurityException("Cannot read payment methods for another user");
        }
        return ResponseEntity.ok(service.listForUser(userId));
    }

    @PutMapping("/{id}/default")
    @Operation(summary = "Mark the given payment method as the user's default")
    public ResponseEntity<SavedPaymentMethod> setDefault(
        @PathVariable Long id,
        @AuthenticationPrincipal Jwt jwt,
        @RequestHeader(value = "X-User-Id", required = false) String headerUserId
    ) {
        String userId = resolveUserId(jwt, headerUserId);
        return ResponseEntity.ok(service.setDefault(userId, id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a saved payment method")
    public ResponseEntity<Void> delete(
        @PathVariable Long id,
        @AuthenticationPrincipal Jwt jwt,
        @RequestHeader(value = "X-User-Id", required = false) String headerUserId
    ) {
        String userId = resolveUserId(jwt, headerUserId);
        service.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    private static String resolveUserId(Jwt jwt, String headerUserId) {
        if (jwt != null && jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
            return jwt.getSubject();
        }
        if (headerUserId != null && !headerUserId.isBlank()) {
            return headerUserId;
        }
        throw new SecurityException("Authenticated user required");
    }
}
