/**
 * Promotion-service participant in the inventory replenishment choreography.
 *
 * <p><strong>Upstream topics consumed</strong>:
 * <ul>
 *   <li>{@code stock.low.detected}</li>
 *   <li>{@code stock.replenished} — compensation trigger.</li>
 * </ul>
 *
 * <p><strong>Downstream topics published</strong>:
 * <ul>
 *   <li>{@code promotion.paused.due-to-stock}</li>
 *   <li>{@code promotion.resumed.due-to-stock}</li>
 * </ul>
 *
 * <p><strong>Local action</strong>: pause active promotions linked to the
 * affected product via the {@code promotion_products} join table when stock
 * is low; resume them when stock returns.
 *
 * <p><strong>Idempotency</strong>: every consumed event is recorded in
 * {@code promotion_consumed_replenishment_event}. Replays are dropped.</p>
 */
package com.ecommerce.promotionservice.saga.replenishment;
