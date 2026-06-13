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
