/**
 * <h1>Inventory Replenishment — Choreography Saga (master javadoc)</h1>
 *
 * <p>This package is the <strong>trigger</strong> side of a choreography saga.
 * Read it side-by-side with
 * {@code services/order-service/src/main/java/com/ecommerce/orderservice/saga/refund}
 * to see how the orchestration sibling solves a similar problem with a
 * coordinator class instead.
 *
 * <h2>What is a choreography saga?</h2>
 * <p>A choreography saga is a distributed transaction in which each
 * participant reacts to events <em>independently</em>. There is no central
 * coordinator. The "workflow" exists only as a graph of Kafka topics and
 * subscribers — read the topic contracts, follow the wires.
 *
 * <h2>This saga in one diagram</h2>
 * <pre>
 *                          ┌─────────────────────────┐
 *                          │     inventory-service   │
 *                          │ {@link com.ecommerce.inventoryservice.saga.replenishment.StockLowDetector}
 *                          │   @Scheduled every 30s  │
 *                          └────────────┬────────────┘
 *                                       │
 *                            stock.low.detected
 *                                       │
 *               ┌───────────────────────┼───────────────────────┐
 *               ▼                       ▼                       ▼
 *      promotion-service         search-service          notification-service
 *      pause active promo        set lowStockPenalty     email admin
 *               │                       │                       │
 *      promotion.paused          search.deboosted        admin.notified
 *      .due-to-stock             .due-to-stock           .due-to-stock
 *
 *  Compensation (when stock returns):  stock.replenished fans out the same
 *  way and each peer reverses what it did. No coordinator. No global state.
 * </pre>
 *
 * <h2>Pros — why you would choose this over orchestration</h2>
 * <ul>
 *   <li><strong>Loose coupling.</strong> {@code inventory-service} does not
 *       know promotion-service or search-service exist. It just publishes
 *       events.</li>
 *   <li><strong>Open for extension.</strong> A new participant (e.g. an
 *       analytics-service that wants to log low-stock incidents) is added by
 *       writing a new {@code @KafkaListener} — no edits anywhere else.</li>
 *   <li><strong>Naturally parallel.</strong> All three peers run their local
 *       action simultaneously. Orchestration serialises by default.</li>
 *   <li><strong>No coordinator failure mode.</strong> There is no single
 *       point of failure or recovery scheduler to maintain.</li>
 * </ul>
 *
 * <h2>Cons — why orchestration may still be the right call</h2>
 * <ul>
 *   <li><strong>Implicit workflow.</strong> No single class describes the
 *       end-to-end flow. Onboarding requires reading every listener.</li>
 *   <li><strong>Hard to debug.</strong> Without a saga state row you must
 *       correlate by event id across multiple service logs (Zipkin helps
 *       but is not free).</li>
 *   <li><strong>Contract drift is silent.</strong> Add a field to
 *       {@link com.ecommerce.inventoryservice.saga.replenishment.StockLowDetectedEvent}
 *       in only one place and the deserialisation explodes inside a peer
 *       that has not redeployed. The orchestrator pattern catches this at
 *       compile time because there is one class.</li>
 *   <li><strong>No global timeout / retry policy.</strong> Each consumer
 *       defines its own. There is no "this saga has been running for 5
 *       minutes, abort" logic.</li>
 *   <li><strong>Easy to create cycles.</strong> An eager peer publishing a
 *       new trigger event in response to its own outcome can re-enter the
 *       saga. Orchestration sequences make this much harder.</li>
 * </ul>
 *
 * <h2>Common pitfalls (each peer must handle)</h2>
 * <ul>
 *   <li><strong>Idempotency.</strong> Kafka delivers at-least-once. Every
 *       consumer dedupes on {@code eventId} via a small per-service
 *       {@code consumed_event} table (or Mongo collection for
 *       notification-service).</li>
 *   <li><strong>Event versioning.</strong> Add fields, never remove. Use
 *       Jackson defaults so old consumers can ignore new fields.</li>
 *   <li><strong>Dead-letter queues.</strong> Poison messages should not
 *       block the partition. Production code wires a DLQ; this learning
 *       version logs and moves on.</li>
 *   <li><strong>Out-of-order events.</strong> {@code stock.replenished} can
 *       arrive before {@code stock.low.detected} after a partition rebalance
 *       — peers must tolerate compensating something they never paused.</li>
 * </ul>
 *
 * <h2>Topics published from this package</h2>
 * <ul>
 *   <li>{@code stock.low.detected} — payload {@link com.ecommerce.inventoryservice.saga.replenishment.StockLowDetectedEvent}</li>
 *   <li>{@code stock.replenished} — payload {@link com.ecommerce.inventoryservice.saga.replenishment.StockReplenishedEvent}</li>
 * </ul>
 *
 * <p>Topics consumed: <em>none</em>. This service is the trigger only.
 * Outcome topics are documented inside each peer's {@code package-info.java}.
 */
package com.ecommerce.inventoryservice.saga.replenishment;
