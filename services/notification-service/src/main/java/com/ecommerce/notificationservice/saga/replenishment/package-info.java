/**
 * Notification-service participant in the inventory replenishment choreography.
 *
 * <p><strong>Upstream topics consumed</strong>:
 * <ul>
 *   <li>{@code stock.low.detected}</li>
 *   <li>{@code stock.replenished}</li>
 * </ul>
 *
 * <p><strong>Downstream topics published</strong>:
 * <ul>
 *   <li>{@code admin.notified.due-to-stock}</li>
 *   <li>{@code admin.stock-back.due-to-stock}</li>
 * </ul>
 *
 * <p><strong>Local action</strong>: send the admin email via the existing
 * {@link com.ecommerce.notificationservice.service.EmailService}. The
 * {@code stock-low-alert} / {@code stock-back-alert} Thymeleaf templates are
 * stubs — the email service mock-logs in dev unless
 * {@code notification.email.enabled=true}.
 *
 * <p><strong>Idempotency</strong>: every consumed event is recorded in the
 * {@code replenishment_consumed_events} Mongo collection. Replays are dropped.
 *
 * <p>For the master javadoc on choreography saga theory, read
 * {@link com.ecommerce.inventoryservice.saga.replenishment}.
 */
package com.ecommerce.notificationservice.saga.replenishment;
