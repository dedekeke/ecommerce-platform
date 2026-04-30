export type OrderStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'PROCESSING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'REFUNDED';

export interface OrderLineItem {
  id: string;
  productId: string;
  productName: string;
  imageUrl?: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
  sku: string;
}

export interface ShippingInfo {
  carrier: string;
  trackingNumber?: string;
  estimatedDelivery?: string;
  address: {
    street: string;
    city: string;
    state: string;
    postalCode: string;
    country: string;
  };
}

export interface PaymentSummary {
  method: string;
  last4?: string;
  subtotal: number;
  shippingCost: number;
  tax: number;
  discount: number;
  total: number;
}

export interface StatusEvent {
  status: OrderStatus;
  timestamp: string;
  note?: string;
}

export interface Order {
  id: string;
  orderNumber: string;
  userId: string;
  status: OrderStatus;
  lineItems: OrderLineItem[];
  shipping: ShippingInfo;
  payment: PaymentSummary;
  timeline: StatusEvent[];
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
