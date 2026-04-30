# Saga Patterns in this Project

This document is **learning material**. It compares the two distributed-transaction
patterns used here — *orchestration* and *choreography* — and points to the
concrete examples in the codebase you can read end-to-end.

A saga is a long-running business transaction split into smaller local
transactions (one per service). Sagas trade ACID guarantees for eventual
consistency, and rely on **compensating actions** instead of rollbacks.

---

## 1. Orchestration vs Choreography

| Dimension | Orchestration | Choreography |
| --- | --- | --- |
| Who drives the flow? | A central coordinator service. | Each service reacts to events. |
| Where is the flow defined? | One class — readable top-to-bottom. | Spread across many event handlers. |
| Coupling | Coordinator knows every participant. | Participants only know the events they emit / consume. |
| Adding a step | Edit the orchestrator. | Subscribe a new service to the event. |
| Debugging | Single trace; centralised log. | Distributed; needs correlation IDs. |
| Risk of cycles | Low — flow is explicit. | High — events can re-enter. |
| Best when | One team owns the workflow; logic is non-trivial. | Many teams own pieces; loose coupling matters. |
| Failure recovery | Coordinator persists state; resumes after crash. | Each service tracks its own progress. |
| Throughput | Limited by coordinator. | Naturally parallel. |

References worth reading:
- Microsoft architecture docs: *Saga distributed transactions pattern*.
- Chris Richardson, *Microservices Patterns*, ch. 4 ("Managing transactions with sagas").
- AWS Step Functions: production-grade orchestration.
- Confluent: *Microservices and the saga pattern* (event-driven choreography).

---

## 2. This Project's Examples

### 2.1 `OrderCreationSaga` — orchestration (original)

- File: `services/order-service/src/main/java/com/ecommerce/orderservice/saga/OrderCreationSaga.java`
- Tests: `services/order-service/src/test/java/com/ecommerce/orderservice/saga/OrderCreationSagaTest.java`
- What it does: cart -> reserve stock -> create order -> apply promotion -> create
  payment intent -> clear cart -> publish `order.created`.
- Style: single class, in-line steps, in-line compensation. Classic but the
  class is already approaching "god object" territory — that's what motivated
  the refactored version below.

### 2.2 `RefundOrchestrator` — orchestration (pedagogical)

- Package: `services/order-service/src/main/java/com/ecommerce/orderservice/saga/refund/`
  - `RefundOrchestrator.java` — the coordinator.
  - `RefundSagaState.java` — JPA entity persisting progress for crash recovery.
  - `RefundSagaContext.java` — per-run scratch DTO.
  - `step/` — one class per step (`ValidateRefundStep`, `ReversePaymentStep`,
    `RestoreInventoryStep`, `UpdateOrderStep`, `NotifyRefundStep`).
  - `RefundSagaRecoveryScheduler.java` — every 5 minutes, picks up sagas stuck
    `IN_PROGRESS` / `COMPENSATING` and resumes them.
- Controller: `services/order-service/src/main/java/com/ecommerce/orderservice/controller/RefundController.java`
  exposes `POST /api/orders/{orderId}/refund` and
  `GET /api/orders/refunds/{sagaId}` (admin-scoped).
- What it does: validate eligibility -> reverse payment (gRPC) ->
  restore inventory (gRPC) -> mark order REFUNDED -> publish
  `refund.completed`.
- Why a second example exists: it shows the *clean* shape of an orchestration
  saga — small `SagaStep` interface, persisted state, idempotent steps,
  recovery scheduler, controller separation. Read it side-by-side with
  `OrderCreationSaga` to see how the same pattern scales.

### 2.3 `InventoryReplenishmentChoreography` — choreography (placeholder)

> Not yet implemented. Will land on the next branch.

- Planned package: `services/inventory-service/src/main/java/com/ecommerce/inventoryservice/saga/replenishment/`.
- Planned scenario: when stock for a product drops below its reorder threshold,
  inventory-service emits `stock.low`. A supplier-service consumes it, places
  a purchase order and emits `purchase-order.placed`. A receiving-service
  consumes that and emits `stock.received`, which inventory-service consumes
  to top up. No coordinator — each service only knows the events it cares
  about.
- Pedagogical contrast points to look for:
  - No saga state row anywhere — each service tracks its own progress.
  - Compensation is itself an event (e.g. `purchase-order.cancelled`).
  - The flow exists only as a sequence of Kafka topics and consumers — there's
    no single class describing it.

---

## 3. When to use which

Rules of thumb:

1. **Default to orchestration when a single team owns the workflow.** The
   coordinator gives you one place to read, debug and instrument the flow.
2. **Choose choreography when teams own the participants but no one owns the
   end-to-end flow.** Loose coupling is worth the cognitive cost.
3. **Combine them.** Big sagas often have an orchestrator at the top and
   choreography between leaf services.
4. **Always persist saga state when running orchestration**, even if it feels
   over-engineered for a small flow. The first time the JVM dies mid-saga you
   will be very glad you did.
5. **Compensations are not rollbacks.** They are real business actions
   (re-charge, restock, send "sorry" email) and must be designed up front.
   The default no-op `compensate()` in `SagaStep` is fine for read-only or
   best-effort steps; everything else needs a thought-through reverse.
6. **Every step must be idempotent.** Recovery schedulers, network retries
   and at-least-once message delivery all replay steps. Use idempotency keys
   (`restorationId`, `refundTransactionId`) and let participants short-circuit
   on duplicate calls.

---

## 4. What's NOT in this project

We deliberately avoid saga frameworks (Axon, Eventuate, Temporal). The
mechanism is hand-rolled in ~500 lines so you can read it. In production code
you might adopt a framework once you have 3+ sagas — but only after you
understand the bare-metal version.
