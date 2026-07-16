import { Order, TimelineEvent } from '../models/order.model';

/**
 * order-service's `OrderResponse` has no per-status-change timestamp log — only
 * `createdAt`, `shippedAt`, `deliveredAt`, and `updatedAt`. This derives a display
 * timeline from those fields plus the current `status`, rather than the aspirational
 * `order.timeline` array the backend never returns.
 */
export function buildOrderTimeline(
  order: Pick<Order, 'status' | 'createdAt' | 'shippedAt' | 'deliveredAt' | 'updatedAt'>
): TimelineEvent[] {
  const events: TimelineEvent[] = [{ status: 'PENDING', timestamp: order.createdAt }];

  if (order.shippedAt) {
    events.push({ status: 'SHIPPED', timestamp: order.shippedAt });
  }
  if (order.deliveredAt) {
    events.push({ status: 'DELIVERED', timestamp: order.deliveredAt });
  }

  const lastStatus = events[events.length - 1].status;
  if (order.status !== lastStatus) {
    events.push({ status: order.status, timestamp: order.updatedAt });
  }

  return events;
}
