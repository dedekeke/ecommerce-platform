export interface CartItem {
  productId: string
  name: string
  price: number
  quantity: number
  image?: string
}

export interface CartState {
  items: CartItem[]
  total: number
  itemCount: number
  addItem: (item: Omit<CartItem, 'quantity'>) => void
  removeItem: (productId: string) => void
  updateQuantity: (productId: string, quantity: number) => void
  clearCart: () => void
}
