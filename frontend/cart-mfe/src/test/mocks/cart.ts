import type { CartResponse } from '../../api/types'

export const mockCartItem1 = {
  id: 'item-1',
  productId: 'prod-1',
  productName: 'Wireless Headphones',
  productSku: 'SKU-HP-001',
  productImageUrl: 'https://via.placeholder.com/80x80?text=Headphones',
  price: 79.99,
  quantity: 2,
  subtotal: 159.98,
}

export const mockCartItem2 = {
  id: 'item-2',
  productId: 'prod-2',
  productName: 'Mechanical Keyboard',
  productSku: 'SKU-KB-002',
  productImageUrl: 'https://via.placeholder.com/80x80?text=Keyboard',
  price: 149.99,
  quantity: 1,
  subtotal: 149.99,
}

export const mockCart: CartResponse = {
  id: 'cart-abc123',
  userId: 'auth0|user-1',
  items: [mockCartItem1, mockCartItem2],
  totalAmount: 309.97,
  totalItems: 3,
  status: 'ACTIVE',
}

export const mockEmptyCart: CartResponse = {
  id: 'cart-empty',
  userId: 'auth0|user-1',
  items: [],
  totalAmount: 0,
  totalItems: 0,
  status: 'ACTIVE',
}
