package com.ecommerce.orderservice.saga.rma;

import com.ecommerce.orderservice.dto.PageResponse;
import com.ecommerce.orderservice.security.UserIdentityResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * REST entrypoints for the RMA saga.
 *
 * <p>Customer endpoints (POST / GET-by-id / GET-by-user) require an
 * authenticated session. Warehouse / admin endpoints (receive, inspect)
 * require admin scope.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/returns")
@RequiredArgsConstructor
@Tag(name = "Returns / RMA", description = "Return Merchandise Authorization saga endpoints")
public class RmaController {

    private final RmaOrchestrator orchestrator;
    private final UserIdentityResolver userIdentityResolver;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Customer requests a return for a delivered order")
    public ResponseEntity<Return> requestReturn(
        @Valid @RequestBody RmaRequest body,
        @RequestHeader(value = "X-User-Id", required = false) String userId,
        @RequestHeader(value = "X-User-Email", required = false) String userEmail,
        @AuthenticationPrincipal Jwt jwt
    ) {
        // The requesting user is the JWT subject — the X-User-Id header is never trusted.
        String resolvedUserId = userIdentityResolver.resolveUserId(userId, jwt);
        log.info("REST: starting RMA saga for order {}", body.orderId());
        List<RmaOrchestrator.LineRequest> lines = body.lines() == null ? null : body.lines().stream()
            .map(l -> new RmaOrchestrator.LineRequest(l.orderItemId(), l.quantity(), l.reason()))
            .toList();
        Return rma = orchestrator.requestReturn(body.orderId(), resolvedUserId, body.reason(), lines, userEmail);
        URI location = URI.create("/api/returns/" + rma.getId());
        return ResponseEntity.accepted().location(location).body(rma);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @Operation(summary = "List returns (admin), optionally filtered by status")
    public ResponseEntity<PageResponse<ReturnSummary>> listReturns(
        @RequestParam(required = false) ReturnStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "requestedAt"));
        Page<ReturnSummary> result = orchestrator.listReturns(status, pageable);
        return ResponseEntity.ok(PageResponse.from(result));
    }

    @GetMapping("/{rmaId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Look up a return by RMA id")
    public ResponseEntity<Return> getReturn(
        @PathVariable String rmaId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return orchestrator.findById(rmaId)
            .map(rma -> {
                // IDOR guard: the RMA must belong to the caller (or the caller is admin).
                userIdentityResolver.assertCanActFor(rma.getUserId(), jwt);
                return ResponseEntity.ok(rma);
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("isAuthenticated() and (#userId == authentication.name or hasAuthority('SCOPE_admin'))")
    @Operation(summary = "List all returns for the given user")
    public ResponseEntity<List<Return>> getUserReturns(@PathVariable String userId) {
        return ResponseEntity.ok(orchestrator.findByUser(userId));
    }

    @PostMapping("/{rmaId}/receive")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @Operation(summary = "Warehouse marks a return parcel as received")
    public ResponseEntity<Return> receive(@PathVariable String rmaId) {
        return ResponseEntity.ok(orchestrator.markReceived(rmaId));
    }

    @PostMapping("/{rmaId}/inspect")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @Operation(summary = "Admin records the inspection outcome (APPROVED / REJECTED)")
    public ResponseEntity<Return> inspect(
        @PathVariable String rmaId,
        @Valid @RequestBody InspectRequest body,
        @RequestHeader(value = "X-User-Email", required = false) String userEmail
    ) {
        Return rma = orchestrator.inspect(rmaId, body.outcome(), body.condition(),
            body.notes(), body.restockingFeePercent(), userEmail);
        return ResponseEntity.ok(rma);
    }

    @ExceptionHandler(RmaException.class)
    public ResponseEntity<Map<String, String>> handleRmaException(RmaException ex) {
        log.warn("RMA validation failed: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    public record RmaRequest(
        @NotBlank @Size(max = 128) String orderId,
        @Size(max = 500) String reason,
        @Valid List<LineItem> lines
    ) {
    }

    /** A single line a customer wants to return (partial-return support). */
    public record LineItem(
        @NotBlank @Size(max = 128) String orderItemId,
        @NotNull @Min(1) Integer quantity,
        @Size(max = 500) String reason
    ) {
    }

    public record InspectRequest(
        @NotBlank @Size(max = 32) String outcome,
        @Size(max = 64) String condition,
        @Size(max = 1000) String notes,
        @DecimalMin(value = "0.0", inclusive = true)
        @DecimalMax(value = "100.0", inclusive = true)
        BigDecimal restockingFeePercent
    ) {
    }
}
