package com.ecommerce.orderservice.saga.rma;

/**
 * Lifecycle of a Return Merchandise Authorization (RMA) saga.
 *
 * <p>Unlike the synchronous refund saga, an RMA is long-running: the
 * customer physically ships an item back, so the saga sits in
 * {@link #AWAITING_SHIPMENT} for hours / days until warehouse confirms
 * receipt. {@code RmaRecoveryScheduler} only re-drives the short
 * transient states ({@link #REQUESTED}, {@link #NOTIFIED},
 * {@link #INSPECTING}); long-poll states are intentionally excluded.</p>
 */
public enum ReturnStatus {
    /** Initial state — customer just hit POST /api/returns. */
    REQUESTED,
    /** Saga published rma.requested to Kafka — customer has the label. */
    NOTIFIED,
    /** Long-poll state: waiting for warehouse to scan the parcel back in. */
    AWAITING_SHIPMENT,
    /** Warehouse has scanned the package; awaiting inspection. */
    RECEIVED,
    /** Admin opened inspection but has not yet recorded an outcome. */
    INSPECTING,
    /** Inspection passed; refund + inventory restore in progress / done. */
    APPROVED,
    /** Inspection failed; ship-back-to-customer in progress / done. */
    REJECTED,
    /** Final happy state for an APPROVED RMA after refund + inventory restore. */
    COMPLETED,
    /** RMA was cancelled before any compensable side effects. */
    CANCELLED,
    /** Saga gave up — manual ops required. */
    FAILED
}
