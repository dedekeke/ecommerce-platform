export interface CartItem {
  productId: string
  name: string
  price: number
  quantity: number
  image?: string
}

/** Result of a successful `POST /api/promotions/validate`, as stored in the cart. */
export interface AppliedPromotion {
  code: string
  discountAmount: number
  promotionName: string | null
}

export interface CartState {
  items: CartItem[]
  total: number
  itemCount: number
  promotionCode: string | null
  discountAmount: number | null
  promotionName: string | null
  addItem: (item: Omit<CartItem, 'quantity'>) => void
  removeItem: (productId: string) => void
  updateQuantity: (productId: string, quantity: number) => void
  clearCart: () => void
  /** Persists a validated promotion so it survives navigation into checkout-mfe. */
  applyPromotion: (promotion: AppliedPromotion) => void
  removePromotion: () => void
}
