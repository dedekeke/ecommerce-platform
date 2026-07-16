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

/** Wire shape of the shipping address accepted by `POST /api/orders` (order-service AddressDto). */
export interface AddressDto {
  street: string
  city: string
  state: string
  postalCode: string
  country: string
}

/**
 * Body of `POST /api/orders` — order-first checkout (see services/order-service CheckoutRequest,
 * PR#122). The cart is never sent; the saga reads the authenticated user's real cart server-side.
 * `userId` is an optional correlation hint only — the JWT subject is authoritative.
 */
export interface CheckoutRequestPayload {
  userId?: string
  shippingAddress: AddressDto
  promotionCode?: string
  userEmail?: string
  userName?: string
}

/**
 * Body of `POST /api/orders/guest` — the unauthenticated guest checkout (see
 * order-service GuestCheckoutRequest). There is no `userId`: the owning identity
 * is derived server-side from `email`, so a guest can never assert who they are.
 * `email` is required + format-validated (the server re-validates) and doubles as
 * the claim key for later account linking.
 */
export interface GuestCheckoutRequestPayload {
  email: string
  shippingAddress: AddressDto
  promotionCode?: string
  userName?: string
}

export interface CheckoutResponseItem {
  productId: string
  productName: string
  price: number
  quantity: number
  subtotal: number
}

/**
 * Response of `POST /api/orders` (order-service CheckoutResponse). The saga creates the order
 * and the Stripe PaymentIntent server-side; `clientSecret` is what Stripe Elements confirms —
 * this app never creates its own PaymentIntent and never collects raw card data (PCI SAQ-A).
 *
 * On an idempotent replay (`HTTP 200`) of an already-completed checkout, `clientSecret` is
 * `null` (not persisted) — callers must not try to mount Stripe Elements with it.
 */
export interface CheckoutResponse {
  orderId: string
  orderNumber: string
  status: string
  currency: string
  subtotal: number
  tax: number
  shippingCost: number
  discountAmount: number | null
  loyaltyDiscount: number | null
  total: number
  paymentIntentId: string | null
  clientSecret: string | null
  items: CheckoutResponseItem[]
  createdAt?: string
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

/**
 * A previously-saved payment method for an authenticated user (see payment-service). `providerId`
 * is the Stripe payment_method id (`pm_...`) used to confirm a PaymentIntent without collecting
 * raw card data (PCI SAQ-A). `GET /api/payments/methods/user/{userId}` only ever returns the
 * requesting user's own methods.
 */
export interface SavedPaymentMethod {
  id: number
  userId: string
  provider: string
  providerId: string
  last4: string
  brand: string
  expMonth: number
  expYear: number
  isDefault: boolean
  createdAt: string
}
