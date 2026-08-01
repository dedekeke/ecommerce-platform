export type OrderStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'PROCESSING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'REFUNDED';

export interface OrderItem {
  productId: string;
  productName: string;
  price: number;
  quantity: number;
  subtotal: number;
}

export interface OrderShippingAddress {
  street: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
}

/**
 * Client-side view of order-service's `OrderResponse` (PR#139/#145). Field naming mirrors the
 * backend record exactly — `orderId` (not `id`), flat `subtotal`/`tax`/`shippingCost`/`total`
 * (no nested `payment` object), flat `carrier`/`trackingNumber`/`shippedAt`/`deliveredAt`
 * (no nested `shipping` object) — since the backend has no per-status timeline, the UI
 * timeline is derived client-side from these fields (see `buildOrderTimeline`).
 */
export interface Order {
  orderId: string;
  orderNumber: string;
  status: OrderStatus;
  currency: string;
  subtotal: number;
  tax: number;
  shippingCost: number;
  discountAmount: number | null;
  loyaltyDiscount: number | null;
  total: number;
  items: OrderItem[];
  shippingAddress: OrderShippingAddress | null;
  paymentIntentId: string | null;
  guestOrder: boolean;
  carrier: string | null;
  trackingNumber: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PagedOrders {
  content: Order[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface OrderFilterParams {
  status?: OrderStatus;
  page: number;
  size: number;
}

/** A single milestone in the client-derived order status timeline. */
export interface TimelineEvent {
  status: OrderStatus;
  timestamp: string;
}
