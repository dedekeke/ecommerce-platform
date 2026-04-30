/**
 * Inventory replenishment — choreography saga (trigger side).
 *
 * <h2>Topics published</h2>
 * <ul>
 *   <li>{@code stock.low.detected} — payload
 *       {@link com.ecommerce.inventoryservice.saga.replenishment.StockLowDetectedEvent}.
 *       Fired when {@code availableQty <= reorderLevel} and we have not
 *       already reported this product as low.</li>
 *   <li>{@code stock.replenished} — payload
 *       {@link com.ecommerce.inventoryservice.saga.replenishment.StockReplenishedEvent}.
 *       Fired when a previously low product crosses back above the threshold;
 *       acts as the compensation trigger for downstream peers.</li>
 * </ul>
 *
 * <p>Topics consumed: <em>none</em>. This service is the trigger only.</p>
 *
 * <h2>Idempotency contract</h2>
 * <p>Every event carries a {@code UUID eventId}. Each peer must dedupe on
 * that id (Kafka delivers at-least-once). Producers must not reuse ids.
 * Edge-trigger semantics in {@link
 * com.ecommerce.inventoryservice.saga.replenishment.StockLowDetector}
 * prevent re-firing the same event every poll cycle — the in-memory
 * {@code reportedLow} set must move to Redis or a shared table for
 * multi-instance deployments.</p>
 */
package com.ecommerce.inventoryservice.saga.replenishment;
