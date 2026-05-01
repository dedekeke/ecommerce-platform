# Day 37 — Returns / RMA Orchestration Saga (§3.8)

## Summary

Built the long-running RMA (Return Merchandise Authorization) saga in
`order-service`, mirroring the existing `RefundOrchestrator` pattern.
The saga differs from refund in that it has a real-world pause —
between AWAITING_SHIPMENT and RECEIVED the customer physically ships
the parcel back, so the saga is split into three operator-driven
checkpoints: `requestReturn`, `markReceived`, `inspect`.

On `inspect(APPROVED)` the orchestrator delegates refund + inventory
restoration to the existing `RefundOrchestrator` (with
`reason=RMA-${rmaNumber}`) — no duplicate plumbing. On
`inspect(REJECTED)` it mocks a ship-back via `MockShippingClient` and
publishes `rma.rejected`.

## Files Created

### order-service (10 files)
- `saga/rma/Return.java` — JPA entity
- `saga/rma/ReturnStatus.java` — lifecycle enum (10 states)
- `saga/rma/ReturnRepository.java`
- `saga/rma/RmaOrchestrator.java` — main service
- `saga/rma/RmaRecoveryScheduler.java` — re-drives stuck transient states
- `saga/rma/RmaController.java` — 5 REST endpoints
- `saga/rma/RmaEvent.java` — Kafka payload
- `saga/rma/RmaEventPublisher.java` — publishes 4 topics
- `saga/rma/MockShippingClient.java` — fake carrier integration
- `saga/rma/RmaException.java`
- `db/migration/V6__Create_returns_table.sql`

### notification-service (5 files)
- `kafka/RmaEventConsumer.java` — 3 listeners (requested / completed / rejected)
- `kafka/event/RmaEvent.java`
- `templates/rma-requested.html`
- `templates/rma-completed.html`
- `templates/rma-rejected.html`

### Tests (5 files / 48 tests)
- `RmaOrchestratorTest` — 18 tests
- `RmaRecoverySchedulerTest` — 4 tests
- `MockShippingClientTest` — 3 tests
- `RmaControllerTest` — 15 tests
- `RmaEventConsumerTest` — 8 tests

## Test Results

```
order-service:        194 tests pass (excluding 3 pre-existing OrderControllerSecurityTest failures)
notification-service: RmaEventConsumerTest 8/8 pass
                      OrderEventConsumerTest failures are pre-existing on master.
```

## Where We Call RefundOrchestrator

`RmaOrchestrator.handleApproved(...)` at
`services/order-service/src/main/java/com/ecommerce/orderservice/saga/rma/RmaOrchestrator.java:208`
calls `refundOrchestrator.startRefund(orderId, "RMA-" + rmaNumber, userEmail)`.
The returned `RefundSagaState` is inspected — COMPLETED maps to
ReturnStatus.COMPLETED, FAILED/COMPENSATED maps to ReturnStatus.FAILED.

## Open Follow-ups

- Real carrier integration: swap `MockShippingClient` for a real
  FedEx / DHL / UPS SDK. The interface is stable.
- Partial returns: today the saga returns the entire order. Need a
  `Return` -> `ReturnLine` 1:N to handle "I want to return only item X".
- Restocking-fee logic: APPROVED currently triggers full refund. A
  future iteration should let the inspector enter a fee % and pass a
  reduced amount to the refund saga.
- Make recovery-state list configurable so AWAITING_SHIPMENT could be
  re-driven after very long deadlines (e.g. 30 days expired = auto-cancel).
- Surface RMA in the customer MFE — currently no UI yet.
