package com.ecommerce.orderservice.saga.refund;

public enum RefundSagaStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    COMPENSATING,
    FAILED,
    COMPENSATED
}
