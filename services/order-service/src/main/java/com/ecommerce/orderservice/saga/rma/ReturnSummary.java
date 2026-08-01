package com.ecommerce.orderservice.saga.rma;

import java.time.LocalDateTime;

/**
 * Serialization-safe projection of a {@link Return} for list endpoints.
 *
 * <p>The entity's {@code lines} association is {@code LAZY}; serializing a
 * detached {@code Return} in the paged list path would trip
 * {@code LazyInitializationException} when Jackson touches {@code getLines()}.
 * This summary carries only scalar columns, so it is safe to map inside the
 * read transaction and serialize afterwards.</p>
 */
public record ReturnSummary(
        String id,
        String rmaNumber,
        String orderId,
        String userId,
        ReturnStatus status,
        String outcome,
        String refundSagaId,
        LocalDateTime requestedAt,
        LocalDateTime updatedAt
) {

    public static ReturnSummary from(Return r) {
        return new ReturnSummary(
                r.getId(),
                r.getRmaNumber(),
                r.getOrderId(),
                r.getUserId(),
                r.getStatus(),
                r.getOutcome(),
                r.getRefundSagaId(),
                r.getRequestedAt(),
                r.getUpdatedAt()
        );
    }
}
