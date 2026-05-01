package com.ecommerce.orderservice.saga.rma;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Customer requests a return for a delivered order")
    public ResponseEntity<Return> requestReturn(
        @Valid @RequestBody RmaRequest body,
        @RequestHeader(value = "X-User-Id", required = false) String userId,
        @RequestHeader(value = "X-User-Email", required = false) String userEmail
    ) {
        log.info("REST: starting RMA saga for order {}", body.orderId());
        Return rma = orchestrator.requestReturn(body.orderId(), userId, body.reason(), userEmail);
        URI location = URI.create("/api/returns/" + rma.getId());
        return ResponseEntity.accepted().location(location).body(rma);
    }

    @GetMapping("/{rmaId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Look up a return by RMA id")
    public ResponseEntity<Return> getReturn(@PathVariable String rmaId) {
        return orchestrator.findById(rmaId)
            .map(ResponseEntity::ok)
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
        Return rma = orchestrator.inspect(rmaId, body.outcome(), body.condition(), body.notes(), userEmail);
        return ResponseEntity.ok(rma);
    }

    @ExceptionHandler(RmaException.class)
    public ResponseEntity<Map<String, String>> handleRmaException(RmaException ex) {
        log.warn("RMA validation failed: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    public record RmaRequest(
        @NotBlank @Size(max = 128) String orderId,
        @Size(max = 500) String reason
    ) {
    }

    public record InspectRequest(
        @NotBlank @Size(max = 32) String outcome,
        @Size(max = 64) String condition,
        @Size(max = 1000) String notes
    ) {
    }
}
