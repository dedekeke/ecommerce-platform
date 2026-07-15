export interface OrderItem {
  productId: string
  quantity: number
  price: number
}

export interface ShippingAddress {
  fullName: string
  line1: string
  line2?: string
  city: string
  state: string
  postalCode: string
  country: string
}

export interface CreateOrderPayload {
  userId: string
  items: OrderItem[]
  shippingAddress: ShippingAddress
  /**
   * A Stripe PaymentIntent id already confirmed via Stripe Elements (see StripeCheckout) — never
   * raw card data. ASSUMPTION: the order-saga contract for consuming this at order-creation time
   * was not finalized as of this change; see orderService.createOrder for the single call site
   * to update once the contract is confirmed.
   */
  paymentMethodId: string
  totalAmount: number
}

export interface Order {
  id: string
  orderNumber: string
  status: 'PENDING' | 'CONFIRMED' | 'PROCESSING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED'
  items: OrderItem[]
  shippingAddress: ShippingAddress
  totalAmount: number
  createdAt?: string
}

export interface CreatePaymentIntentPayload {
  orderId: string
  userId: string
  amount: number
  currency: string
}

export interface PaymentIntentResponse {
  paymentId: number
  paymentIntentId: string
  clientSecret: string
  status: string
}
