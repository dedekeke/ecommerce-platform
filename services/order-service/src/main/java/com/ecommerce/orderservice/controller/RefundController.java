package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.dto.PageResponse;
import com.ecommerce.orderservice.saga.refund.RefundOrchestrator;
import com.ecommerce.orderservice.saga.refund.RefundSagaState;
import com.ecommerce.orderservice.saga.refund.RefundSagaStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Order Refunds", description = "Refund saga endpoints (orchestration sample)")
public class RefundController {

    private final RefundOrchestrator orchestrator;

    @PostMapping("/{orderId}/refund")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @Operation(summary = "Start an orchestrated refund saga for the given order")
    public ResponseEntity<RefundSagaState> startRefund(
        @PathVariable String orderId,
        @Valid @RequestBody RefundRequest body,
        @RequestHeader(value = "X-User-Email", required = false) String userEmail
    ) {
        log.info("REST: starting refund saga for order {}", orderId);
        RefundSagaState state = orchestrator
            .startRefund(orderId, body == null ? null : body.reason(), userEmail);
        URI location = URI.create("/api/orders/refunds/" + state.getId());
        return ResponseEntity.accepted().location(location).body(state);
    }

    @GetMapping("/refunds")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @Operation(summary = "List refund sagas (admin), optionally filtered by status")
    public ResponseEntity<PageResponse<RefundSagaState>> listRefunds(
        @RequestParam(required = false) RefundSagaStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<RefundSagaState> result = orchestrator.listSagas(status, pageable);
        return ResponseEntity.ok(PageResponse.from(result));
    }

    @GetMapping("/refunds/{sagaId}")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @Operation(summary = "Look up a refund saga by id")
    public ResponseEntity<RefundSagaState> getRefund(@PathVariable String sagaId) {
        return orchestrator.findSaga(sagaId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record RefundRequest(@Size(max = 500) String reason) {
    }
}
