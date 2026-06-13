package com.ecommerce.orderservice.saga.rma;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.repository.OrderRepository;
import com.ecommerce.orderservice.saga.refund.RefundOrchestrator;
import com.ecommerce.orderservice.saga.refund.RefundSagaState;
import com.ecommerce.orderservice.saga.refund.RefundSagaStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrator for the long-running Returns / RMA saga (§3.8).
 *
 * <p>Unlike {@code RefundOrchestrator}, this saga has a real-world
 * pause: between {@link ReturnStatus#AWAITING_SHIPMENT} and
 * {@link ReturnStatus#RECEIVED} the customer physically ships the
 * package. We therefore split the flow into three operator-driven
 * checkpoints:</p>
 *
 * <ol>
 *   <li>{@link #requestReturn} — eligibility, persist row, mock label,
 *       publish {@code rma.requested}, advance to AWAITING_SHIPMENT.</li>
 *   <li>{@link #markReceived} — admin/warehouse confirms parcel arrival.</li>
 *   <li>{@link #inspect} — admin records APPROVED / REJECTED, which
 *       either delegates to {@link RefundOrchestrator} (refund + inventory)
 *       or mocks a ship-back via {@link MockShippingClient}.</li>
 * </ol>
 *
 * <p>Compensations in this saga are mostly nominal — until the inspect
 * step, no money or stock has moved. The interesting compensation case
 * is when the downstream RefundOrchestrator ends FAILED / COMPENSATED;
 * we surface that by marking the RMA {@link ReturnStatus#FAILED} so ops
 * can intervene.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RmaOrchestrator {

    public static final int RETURN_WINDOW_DAYS = 30;
    public static final String OUTCOME_APPROVED = "APPROVED";
    public static final String OUTCOME_REJECTED = "REJECTED";

    private static final Set<ReturnStatus> ACTIVE_BLOCKERS = Set.of(
        ReturnStatus.REQUESTED,
        ReturnStatus.NOTIFIED,
        ReturnStatus.AWAITING_SHIPMENT,
        ReturnStatus.RECEIVED,
        ReturnStatus.INSPECTING,
        ReturnStatus.APPROVED,
        ReturnStatus.REJECTED,
        ReturnStatus.COMPLETED
    );

    private final ReturnRepository returnRepository;
    private final OrderRepository orderRepository;
    private final RefundOrchestrator refundOrchestrator;
    private final MockShippingClient shippingClient;
    private final RmaEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Start the saga: validate eligibility, persist the Return row, mint
     * a label, publish {@code rma.requested}, advance to
     * {@link ReturnStatus#AWAITING_SHIPMENT}.
     *
     * @throws RmaException if the order is ineligible.
     */
    @Transactional
    public Return requestReturn(String orderId, String userId, String reason, String userEmail) {
        return requestReturn(orderId, userId, reason, null, userEmail);
    }

    /**
     * Partial-return variant: {@code lineRequests} names the order items and
     * quantities to return. When null/empty the return covers the whole order
     * (no lines persisted — backward compatible). Each requested line is
     * validated against the order's items and snapshotted with the item's
     * unit price so the refund can sum approved lines later.
     */
    @Transactional
    public Return requestReturn(String orderId, String userId, String reason,
                                List<LineRequest> lineRequests, String userEmail) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RmaException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new RmaException("Order must be DELIVERED to request a return; current status: "
                + order.getStatus());
        }
        if (order.getCreatedAt() == null
            || Duration.between(order.getCreatedAt(), LocalDateTime.now(clock))
                .toDays() > RETURN_WINDOW_DAYS) {
            throw new RmaException("Return window has expired (" + RETURN_WINDOW_DAYS + " days)");
        }

        List<Return> active = returnRepository.findByOrderIdAndStatusIn(
            orderId, List.copyOf(ACTIVE_BLOCKERS));
        if (!active.isEmpty()) {
            throw new RmaException("An active return already exists for order " + orderId);
        }

        String rmaNumber = generateRmaNumber();
        Return rma = Return.builder()
            .rmaNumber(rmaNumber)
            .orderId(orderId)
            .userId(userId != null ? userId : order.getUserId())
            .reason(reason)
            .status(ReturnStatus.REQUESTED)
            .build();
        attachLines(rma, order, lineRequests);
        rma = returnRepository.save(rma);

        // Step 2: mock-call shipping API for a label. Failure here aborts
        // before any notification fires, so no compensation is needed.
        try {
            String labelUrl = shippingClient.generateReturnLabel(rmaNumber, orderId);
            rma.setReturnLabelUrl(labelUrl);
        } catch (Exception e) {
            log.error("Mock label generation failed for RMA {} — marking FAILED", rmaNumber, e);
            rma.setStatus(ReturnStatus.FAILED);
            rma.setFailureReason("Label generation failed: " + e.getMessage());
            return returnRepository.save(rma);
        }

        // Step 3: best-effort notify. We optimistically advance state even
        // if the publisher swallows an error internally — the saga still
        // owns the DB row and can be retried from the recovery scheduler.
        try {
            eventPublisher.publishRequested(buildEvent(rma, order, userEmail, null, null));
            rma.setStatus(ReturnStatus.NOTIFIED);
        } catch (Exception e) {
            log.warn("Notification publish failed for RMA {} — continuing", rmaNumber, e);
            rma.setStatus(ReturnStatus.NOTIFIED);
        }

        rma.setStatus(ReturnStatus.AWAITING_SHIPMENT);
        return returnRepository.save(rma);
    }

    /**
     * Warehouse-driven step: parcel scanned in, advance to
     * {@link ReturnStatus#RECEIVED} and publish {@code rma.received}.
     */
    @Transactional
    public Return markReceived(String rmaId) {
        Return rma = mustFind(rmaId);
        if (rma.getStatus() != ReturnStatus.AWAITING_SHIPMENT) {
            throw new RmaException("Return cannot be marked RECEIVED from status " + rma.getStatus());
        }
        rma.setStatus(ReturnStatus.RECEIVED);
        rma.setReceivedAt(LocalDateTime.now(clock));
        rma = returnRepository.save(rma);

        Order order = orderRepository.findById(rma.getOrderId()).orElse(null);
        try {
            eventPublisher.publishReceived(buildEvent(rma, order, null, null, null));
        } catch (Exception e) {
            log.warn("Failed to publish rma.received for {} — continuing", rma.getRmaNumber(), e);
        }
        return rma;
    }

    /**
     * Inspection outcome dispatch.
     *
     * <ul>
     *   <li>APPROVED → delegate refund + inventory restore to the existing
     *       {@link RefundOrchestrator}; mark COMPLETED on success or
     *       FAILED if the downstream saga did not COMPLETE.</li>
     *   <li>REJECTED → mock-call carrier to ship back and publish
     *       {@code rma.rejected}.</li>
     * </ul>
     */
    @Transactional
    public Return inspect(String rmaId, String outcome, String condition, String notes, String userEmail) {
        return inspect(rmaId, outcome, condition, notes, null, userEmail);
    }

    /**
     * Inspection with a restocking fee. The fee is validated 0..100 and, on an
     * APPROVED outcome, deducted from the refund by the
     * {@link RefundOrchestrator}.
     */
    @Transactional
    public Return inspect(String rmaId, String outcome, String condition, String notes,
                          BigDecimal restockingFeePercent, String userEmail) {
        Return rma = mustFind(rmaId);
        if (rma.getStatus() != ReturnStatus.RECEIVED && rma.getStatus() != ReturnStatus.INSPECTING) {
            throw new RmaException("Return cannot be inspected from status " + rma.getStatus());
        }
        if (outcome == null) {
            throw new RmaException("Inspection outcome is required");
        }
        String normalized = outcome.toUpperCase(Locale.ROOT);
        if (!OUTCOME_APPROVED.equals(normalized) && !OUTCOME_REJECTED.equals(normalized)) {
            throw new RmaException("Invalid outcome: " + outcome);
        }
        validateRestockingFee(restockingFeePercent);

        rma.setStatus(ReturnStatus.INSPECTING);
        rma.setOutcome(normalized);
        rma.setCondition(condition);
        rma.setNotes(notes);
        rma.setRestockingFeePercent(restockingFeePercent);
        rma.setInspectedAt(LocalDateTime.now(clock));
        rma = returnRepository.save(rma);

        Order order = orderRepository.findById(rma.getOrderId()).orElse(null);

        if (OUTCOME_APPROVED.equals(normalized)) {
            return handleApproved(rma, order, userEmail);
        } else {
            return handleRejected(rma, order, userEmail);
        }
    }

    private Return handleApproved(Return rma, Order order, String userEmail) {
        rma.setStatus(ReturnStatus.APPROVED);
        // An APPROVED inspection approves every requested line. Per-line
        // rejection would be a future refinement; for now an inspector who
        // wants to reject part of a return rejects the whole RMA.
        if (rma.getLines() != null) {
            rma.getLines().forEach(line -> line.setApproved(true));
        }
        rma = returnRepository.save(rma);

        // Delegate refund + inventory restoration to the existing saga.
        // Reason carries the RMA number so downstream auditors can trace.
        // Partial returns pass the approved-line total as the refund base;
        // whole-order returns (no lines) leave it null so the full order
        // total is refunded. The restocking fee is deducted downstream.
        try {
            BigDecimal refundBase = rma.getLines() != null && !rma.getLines().isEmpty()
                ? rma.approvedLinesTotal()
                : null;
            RefundSagaState refundState = refundOrchestrator.startRefund(
                rma.getOrderId(),
                "RMA-" + rma.getRmaNumber(),
                userEmail,
                refundBase,
                rma.getRestockingFeePercent());
            rma.setRefundSagaId(refundState.getId());

            if (refundState.getStatus() == RefundSagaStatus.COMPLETED) {
                rma.setStatus(ReturnStatus.COMPLETED);
                try {
                    eventPublisher.publishCompleted(buildEvent(rma, order, userEmail, null, null));
                } catch (Exception e) {
                    log.warn("Failed to publish rma.completed for {}", rma.getRmaNumber(), e);
                }
            } else {
                // Refund saga ran but did not COMPLETE — could be IN_PROGRESS
                // (async) or already FAILED / COMPENSATED. Recovery scheduler
                // will retry from APPROVED until refund settles.
                if (refundState.getStatus() == RefundSagaStatus.FAILED
                    || refundState.getStatus() == RefundSagaStatus.COMPENSATED) {
                    rma.setStatus(ReturnStatus.FAILED);
                    rma.setFailureReason("Downstream refund saga ended " + refundState.getStatus()
                        + ": " + refundState.getFailureReason());
                }
            }
        } catch (Exception e) {
            log.error("RefundOrchestrator failed for RMA {} — marking FAILED", rma.getRmaNumber(), e);
            rma.setStatus(ReturnStatus.FAILED);
            rma.setFailureReason("Refund delegation error: " + e.getMessage());
        }
        return returnRepository.save(rma);
    }

    private Return handleRejected(Return rma, Order order, String userEmail) {
        rma.setStatus(ReturnStatus.REJECTED);
        try {
            shippingClient.shipBackToCustomer(rma.getRmaNumber(), rma.getOrderId());
        } catch (Exception e) {
            log.error("Mock ship-back failed for RMA {} — saving REJECTED anyway", rma.getRmaNumber(), e);
        }
        rma = returnRepository.save(rma);
        try {
            eventPublisher.publishRejected(buildEvent(rma, order, userEmail, OUTCOME_REJECTED, rma.getNotes()));
        } catch (Exception e) {
            log.warn("Failed to publish rma.rejected for {}", rma.getRmaNumber(), e);
        }
        return rma;
    }

    /**
     * Re-drive a saga that the recovery scheduler picked up as stuck.
     * Only states REQUESTED / NOTIFIED / INSPECTING are resumable — they
     * encode in-flight transient progress where the JVM may have crashed
     * mid-step. AWAITING_SHIPMENT is intentionally excluded (it's
     * supposed to last days).
     */
    @Transactional
    public Return resume(Return rma) {
        log.info("Resuming RMA saga {} from status {}", rma.getRmaNumber(), rma.getStatus());
        Order order = orderRepository.findById(rma.getOrderId()).orElse(null);
        switch (rma.getStatus()) {
            case REQUESTED -> {
                if (rma.getReturnLabelUrl() == null) {
                    rma.setReturnLabelUrl(shippingClient.generateReturnLabel(
                        rma.getRmaNumber(), rma.getOrderId()));
                }
                eventPublisher.publishRequested(buildEvent(rma, order, null, null, null));
                rma.setStatus(ReturnStatus.AWAITING_SHIPMENT);
                return returnRepository.save(rma);
            }
            case NOTIFIED -> {
                rma.setStatus(ReturnStatus.AWAITING_SHIPMENT);
                return returnRepository.save(rma);
            }
            case INSPECTING -> {
                // Outcome already chosen but post-inspection side effects
                // (refund / ship-back) never finished. Replay the dispatch.
                if (OUTCOME_APPROVED.equals(rma.getOutcome())) {
                    return handleApproved(rma, order, null);
                } else if (OUTCOME_REJECTED.equals(rma.getOutcome())) {
                    return handleRejected(rma, order, null);
                }
                return rma;
            }
            default -> {
                return rma;
            }
        }
    }

    public Optional<Return> findById(String rmaId) {
        return returnRepository.findById(rmaId);
    }

    public List<Return> findByUser(String userId) {
        return returnRepository.findByUserId(userId);
    }

    /**
     * Build and attach {@link ReturnLine}s from the request, validating each
     * against the order's items and snapshotting the unit price. Returned
     * quantity may not exceed the quantity originally ordered.
     */
    private void attachLines(Return rma, Order order, List<LineRequest> lineRequests) {
        if (lineRequests == null || lineRequests.isEmpty()) {
            return;
        }
        Map<String, OrderItem> itemsById = order.getItems().stream()
            .collect(Collectors.toMap(OrderItem::getId, item -> item, (a, b) -> a));

        for (LineRequest req : lineRequests) {
            OrderItem item = itemsById.get(req.orderItemId());
            if (item == null) {
                throw new RmaException("Order item not found on order " + order.getId()
                    + ": " + req.orderItemId());
            }
            if (req.quantity() == null || req.quantity() < 1) {
                throw new RmaException("Return quantity must be >= 1 for item " + req.orderItemId());
            }
            if (req.quantity() > item.getQuantity()) {
                throw new RmaException("Cannot return " + req.quantity() + " of item "
                    + req.orderItemId() + " — only " + item.getQuantity() + " ordered");
            }
            rma.addLine(ReturnLine.builder()
                .orderItemId(item.getId())
                .productId(item.getProductId())
                .quantity(req.quantity())
                .unitPrice(item.getPrice())
                .reason(req.reason())
                .approved(false)
                .build());
        }
    }

    private void validateRestockingFee(BigDecimal feePercent) {
        if (feePercent == null) {
            return;
        }
        if (feePercent.signum() < 0 || feePercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new RmaException("restockingFeePercent must be between 0 and 100, got " + feePercent);
        }
    }

    private Return mustFind(String rmaId) {
        return returnRepository.findById(rmaId)
            .orElseThrow(() -> new RmaException("Return not found: " + rmaId));
    }

    private RmaEvent buildEvent(Return rma, Order order, String userEmail, String outcome, String notes) {
        return RmaEvent.builder()
            .rmaId(rma.getId())
            .rmaNumber(rma.getRmaNumber())
            .orderId(rma.getOrderId())
            .orderNumber(order != null ? order.getOrderNumber() : null)
            .userId(rma.getUserId())
            .userEmail(userEmail)
            .returnLabelUrl(rma.getReturnLabelUrl())
            .reason(rma.getReason())
            .outcome(outcome)
            .notes(notes)
            .occurredAt(LocalDateTime.now(clock))
            .build();
    }

    private String generateRmaNumber() {
        return "RMA-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
    }

    /** A requested return line: which order item, how many, and why. */
    public record LineRequest(String orderItemId, Integer quantity, String reason) {
    }
}
