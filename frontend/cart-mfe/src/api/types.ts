/** Wire shape of cart-service `CartItemResponse` (services/cart-service dto/CartItemResponse.java). */
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

/** Wire shape of cart-service `CartResponse` (services/cart-service dto/CartResponse.java). */
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

export interface AddItemPayload {
  productId: string
  quantity: number
}

export interface UpdateItemPayload {
  quantity: number
}

/** Wire shape of `POST /api/promotions/validate` (promotion-service `PromotionValidationRequest`). */
export interface PromotionValidationRequest {
  code: string
  purchaseAmount: number
  categoryId?: number
}

/** Wire shape of `POST /api/promotions/validate`'s response (promotion-service `DiscountResult`). */
export interface PromotionValidationResponse {
  valid: boolean
  message: string
  discountAmount: number | null
  finalAmount: number | null
  promotionCode: string | null
  promotionName: string | null
}
