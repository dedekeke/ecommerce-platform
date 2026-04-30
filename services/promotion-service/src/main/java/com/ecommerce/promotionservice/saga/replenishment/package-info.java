/**
 * Promotion-service participant in the inventory replenishment choreography.
 *
 * <p><strong>Upstream topic consumed</strong>:
 * <ul>
 *   <li>{@code stock.low.detected} — published by inventory-service.</li>
 *   <li>{@code stock.replenished} — compensation trigger.</li>
 * </ul>
 *
 * <p><strong>Downstream topics published</strong>:
 * <ul>
 *   <li>{@code promotion.paused.due-to-stock}</li>
 *   <li>{@code promotion.resumed.due-to-stock}</li>
 * </ul>
 *
 * <p><strong>Local action</strong>: pause active promotions targeting the
 * affected product when stock is low; resume them when stock returns.
 *
 * <p><strong>Idempotency</strong>: every consumed event is recorded in
 * {@code promotion_consumed_replenishment_event}. Replays are dropped.
 *
 * <p>For the master javadoc on choreography saga theory, read
 * {@link com.ecommerce.inventoryservice.saga.replenishment} (the trigger
 * service). For the orchestration sibling read
 * {@code com.ecommerce.orderservice.saga.refund}.
 */
package com.ecommerce.promotionservice.saga.replenishment;
