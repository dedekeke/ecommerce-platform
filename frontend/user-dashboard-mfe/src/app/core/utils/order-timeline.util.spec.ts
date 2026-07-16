import { buildOrderTimeline } from './order-timeline.util';
import { Order } from '../models/order.model';

const base: Pick<Order, 'status' | 'createdAt' | 'shippedAt' | 'deliveredAt' | 'updatedAt'> = {
  status: 'PENDING',
  createdAt: '2026-01-01T00:00:00Z',
  shippedAt: null,
  deliveredAt: null,
  updatedAt: '2026-01-01T00:00:00Z',
};

describe('buildOrderTimeline', () => {
  it('should return only the created event when status is PENDING and nothing shipped', () => {
    const timeline = buildOrderTimeline(base);
    expect(timeline).toEqual([{ status: 'PENDING', timestamp: '2026-01-01T00:00:00Z' }]);
  });

  it('should append the current status at updatedAt when no shippedAt/deliveredAt exist', () => {
    const timeline = buildOrderTimeline({ ...base, status: 'CONFIRMED', updatedAt: '2026-01-02T00:00:00Z' });
    expect(timeline).toEqual([
      { status: 'PENDING', timestamp: '2026-01-01T00:00:00Z' },
      { status: 'CONFIRMED', timestamp: '2026-01-02T00:00:00Z' },
    ]);
  });

  it('should append a SHIPPED event at shippedAt without duplicating it via status', () => {
    const timeline = buildOrderTimeline({
      ...base,
      status: 'SHIPPED',
      shippedAt: '2026-01-03T00:00:00Z',
      updatedAt: '2026-01-03T00:00:00Z',
    });
    expect(timeline).toEqual([
      { status: 'PENDING', timestamp: '2026-01-01T00:00:00Z' },
      { status: 'SHIPPED', timestamp: '2026-01-03T00:00:00Z' },
    ]);
  });

  it('should append both SHIPPED and DELIVERED events in order', () => {
    const timeline = buildOrderTimeline({
      ...base,
      status: 'DELIVERED',
      shippedAt: '2026-01-03T00:00:00Z',
      deliveredAt: '2026-01-05T00:00:00Z',
      updatedAt: '2026-01-05T00:00:00Z',
    });
    expect(timeline).toEqual([
      { status: 'PENDING', timestamp: '2026-01-01T00:00:00Z' },
      { status: 'SHIPPED', timestamp: '2026-01-03T00:00:00Z' },
      { status: 'DELIVERED', timestamp: '2026-01-05T00:00:00Z' },
    ]);
  });

  it('should append a terminal CANCELLED status even without shipping timestamps', () => {
    const timeline = buildOrderTimeline({
      ...base,
      status: 'CANCELLED',
      updatedAt: '2026-01-02T00:00:00Z',
    });
    expect(timeline).toEqual([
      { status: 'PENDING', timestamp: '2026-01-01T00:00:00Z' },
      { status: 'CANCELLED', timestamp: '2026-01-02T00:00:00Z' },
    ]);
  });

  it('should not duplicate the final event when status matches the last derived milestone', () => {
    const timeline = buildOrderTimeline({
      ...base,
      status: 'DELIVERED',
      shippedAt: '2026-01-03T00:00:00Z',
      deliveredAt: '2026-01-05T00:00:00Z',
      updatedAt: '2026-01-06T00:00:00Z',
    });
    expect(timeline.length).toBe(3);
    expect(timeline[2]).toEqual({ status: 'DELIVERED', timestamp: '2026-01-05T00:00:00Z' });
  });
});
