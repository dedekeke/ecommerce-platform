// API Response Types

export interface ApiResponse<T> {
  data: T
  message?: string
  timestamp?: string
}

export interface PaginatedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface ApiError {
  status: number
  message: string
  timestamp?: string
  path?: string
  errors?: Record<string, string[]>
}

// Product Types
export interface Product {
  id: string
  sku: string
  name: string
  description: string
  price: number
  currency: string
  category: Category
  images: string[]
  stockQuantity: number
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface Category {
  id: string
  name: string
  slug: string
  parentId?: string
}

export interface ProductSearchParams {
  query?: string
  categoryId?: string
  minPrice?: number
  maxPrice?: number
  page?: number
  size?: number
  sort?: string
}

// Cart Types — wire shapes of cart-service CartResponse/CartItemResponse
// (services/cart-service dto/CartResponse.java, dto/CartItemResponse.java)
export interface CartResponse {
  id: string
  userId: string
  items: CartItemResponse[]
  totalAmount: number
  totalItems: number
  status: string
  expiresAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface CartItemResponse {
  id: string
  productId: string
  productName: string
  productSku: string | null
  productImageUrl: string | null
  price: number
  quantity: number
  subtotal: number
  addedAt?: string
  updatedAt?: string
}

export interface AddToCartRequest {
  productId: string
  quantity: number
}

export interface UpdateCartItemRequest {
  quantity: number
}

// Order Types
export interface Order {
  id: string
  orderNumber: string
  userId: string
  items: OrderItem[]
  subtotal: number
  tax: number
  shippingCost: number
  total: number
  status: OrderStatus
  shippingAddress: Address
  paymentIntentId?: string
  promotionCode?: string
  discountAmount?: number
  createdAt: string
  updatedAt: string
}

export interface OrderItem {
  productId: string
  productName: string
  productSku: string
  price: number
  quantity: number
  subtotal: number
}

export type OrderStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'PROCESSING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'REFUNDED'

export interface CreateOrderRequest {
  shippingAddressId: string
  promotionCode?: string
}

export interface OrderSearchParams {
  status?: OrderStatus
  page?: number
  size?: number
  sort?: string
}

// User Types
export interface UserProfile {
  id: string
  auth0Id: string
  email: string
  firstName?: string
  lastName?: string
  phoneNumber?: string
  defaultAddressId?: string
  addresses: Address[]
  createdAt: string
  updatedAt: string
}

export interface Address {
  id: string
  label?: string
  street: string
  city: string
  state: string
  postalCode: string
  country: string
  isDefault: boolean
}

export interface UpdateUserProfileRequest {
  firstName?: string
  lastName?: string
  phoneNumber?: string
  defaultAddressId?: string
}

export interface CreateAddressRequest {
  label?: string
  street: string
  city: string
  state: string
  postalCode: string
  country: string
  isDefault?: boolean
}

export type UpdateAddressRequest = Partial<CreateAddressRequest>

// Promotion Types
export interface Promotion {
  id: string
  code: string
  name: string
  description?: string
  type: PromotionType
  discountValue: number
  minPurchaseAmount?: number
  maxUses?: number
  currentUses: number
  startDate: string
  endDate: string
  active: boolean
}

export type PromotionType = 'PERCENTAGE' | 'FIXED_AMOUNT' | 'BUY_X_GET_Y'

export interface ValidatePromotionRequest {
  code: string
  subtotal: number
}

export interface ValidatePromotionResponse {
  valid: boolean
  promotion?: Promotion
  discountAmount?: number
  message?: string
}
