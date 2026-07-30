export interface CartItemResponse {
  itemId: string
  productId: string
  name: string
  price: number
  quantity: number
  image?: string
}

export interface CartResponse {
  cartId: string
  items: CartItemResponse[]
  total: number
  itemCount: number
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
