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
 * backend record exactly — `orderId` (not `id`), flat `subtotal`/`tax`/`shippingCost`/`total`.
 * Deliberately has NO `customerName`/`customerEmail`/`userId` — `OrderResponse` strips ownership
 * and PII fields by design (see order-service's Javadoc); the admin UI cannot show customer
 * identity from this endpoint alone.
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

/**
 * Admin-list view of an order, mirroring order-service's `AdminOrderResponse`
 * (admin list endpoint `GET /api/orders`). Unlike the customer-facing `Order`
 * (`OrderResponse`), it RESTORES the customer identity an admin needs to manage
 * an order — `userId`, `guestEmail`, and the `guestOrder` flag — while the
 * backend still withholds payment secrets. It also carries a precomputed
 * `itemCount` alongside the item lines.
 */
export interface AdminOrder {
  orderId: string;
  orderNumber: string;
  userId: string;
  guestEmail: string | null;
  guestOrder: boolean;
  status: OrderStatus;
  currency: string;
  total: number;
  itemCount: number;
  items: OrderItem[];
  carrier: string | null;
  trackingNumber: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PagedAdminOrders {
  content: AdminOrder[];
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

/** Sent as a query param (`?status=`) — order-service's status endpoint is `@RequestParam`, not a JSON body. */
export interface UpdateOrderStatusPayload {
  status: OrderStatus;
}
