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
 * <p><strong>Local action</strong>: set / clear the {@code lowStockPenalty}
 * flag on the {@link com.ecommerce.searchservice.document.ProductDocument}.
 * The relevance query in
 * {@link com.ecommerce.searchservice.service.ProductSearchService} multiplies
 * the score of flagged documents by 0.5 so out-of-stock items rank lower
 * without being filtered out entirely.
 *
 * <p><strong>Idempotency</strong>: Redis-backed
 * {@link com.ecommerce.searchservice.saga.replenishment.ConsumedEventStore}
 * (SETNX with TTL) survives JVM restarts and is safe across multiple
 * search-service instances.
 */
package com.ecommerce.searchservice.saga.replenishment;
