/**
 * Search-service participant in the inventory replenishment choreography.
 *
 * <p><strong>Upstream topics consumed</strong>:
 * <ul>
 *   <li>{@code stock.low.detected}</li>
 *   <li>{@code stock.replenished}</li>
 * </ul>
 *
 * <p><strong>Downstream topics published</strong>:
 * <ul>
 *   <li>{@code search.deboosted.due-to-stock}</li>
 *   <li>{@code search.reboosted.due-to-stock}</li>
 * </ul>
 *
 * <p><strong>Local action</strong> (stubbed): apply / clear a low-stock
 * penalty on the {@link com.ecommerce.searchservice.document.ProductDocument}
 * so the item ranks lower while inventory cannot fulfil it. The full ranking
 * change is left as a follow-up — the listener itself is wired correctly.
 *
 * <p><strong>Idempotency</strong>: in-memory dedup via
 * {@link com.ecommerce.searchservice.saga.replenishment.ConsumedEventStore}.
 * In production this would move to Redis or a tiny ES dedup index with TTL.
 *
 * <p>For the master javadoc on choreography saga theory, read
 * {@link com.ecommerce.inventoryservice.saga.replenishment}.
 */
package com.ecommerce.searchservice.saga.replenishment;
