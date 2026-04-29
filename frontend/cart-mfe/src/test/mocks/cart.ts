import type { CartResponse } from '../../api/types'

export const mockCartItem1 = {
  itemId: 'item-1',
  productId: 'prod-1',
  name: 'Wireless Headphones',
  price: 79.99,
  quantity: 2,
  image: 'https://via.placeholder.com/80x80?text=Headphones',
}

export const mockCartItem2 = {
  itemId: 'item-2',
  productId: 'prod-2',
  name: 'Mechanical Keyboard',
  price: 149.99,
  quantity: 1,
  image: 'https://via.placeholder.com/80x80?text=Keyboard',
}

export const mockCart: CartResponse = {
  cartId: 'cart-abc123',
  items: [mockCartItem1, mockCartItem2],
  total: 309.97,
  itemCount: 3,
}

export const mockEmptyCart: CartResponse = {
  cartId: 'cart-empty',
  items: [],
  total: 0,
  itemCount: 0,
}
