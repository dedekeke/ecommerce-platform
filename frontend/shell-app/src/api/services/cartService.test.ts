import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { cartService } from './cartService'
import { apiClient } from '../apiClient'
import type { CartResponse, AddToCartRequest, UpdateCartItemRequest } from '../types'

vi.mock('../apiClient', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

const mockCartResponse: CartResponse = {
  id: 'cart-1',
  userId: 'user-1',
  items: [
    {
      productId: 'prod-1',
      name: 'Test Product',
      price: 99.99,
      quantity: 2,
      image: 'image.jpg',
      subtotal: 199.98,
    },
    {
      productId: 'prod-2',
      name: 'Another Product',
      price: 49.99,
      quantity: 1,
      image: 'image2.jpg',
      subtotal: 49.99,
    },
  ],
  subtotal: 249.97,
  createdAt: '2025-01-01T00:00:00Z',
  updatedAt: '2025-01-01T00:00:00Z',
  expiresAt: '2025-01-08T00:00:00Z',
}

describe('CartService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('getCart', () => {
    it('should fetch the current user cart', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockCartResponse })

      const result = await cartService.getCart()

      expect(apiClient.get).toHaveBeenCalledWith('/cart')
      expect(result).toEqual(mockCartResponse)
    })

    it('should handle empty cart', async () => {
      const emptyCart: CartResponse = {
        ...mockCartResponse,
        items: [],
        subtotal: 0,
      }
      vi.mocked(apiClient.get).mockResolvedValue({ data: emptyCart })

      const result = await cartService.getCart()

      expect(result.items).toHaveLength(0)
      expect(result.subtotal).toBe(0)
    })
  })

  describe('addItem', () => {
    it('should add item to cart', async () => {
      vi.mocked(apiClient.post).mockResolvedValue({ data: mockCartResponse })

      const request: AddToCartRequest = {
        productId: 'prod-1',
        quantity: 2,
      }

      const result = await cartService.addItem(request)

      expect(apiClient.post).toHaveBeenCalledWith('/cart/items', request)
      expect(result).toEqual(mockCartResponse)
    })

    it('should add item with default quantity of 1', async () => {
      vi.mocked(apiClient.post).mockResolvedValue({ data: mockCartResponse })

      const result = await cartService.addItem({ productId: 'prod-1', quantity: 1 })

      expect(apiClient.post).toHaveBeenCalledWith('/cart/items', {
        productId: 'prod-1',
        quantity: 1,
      })
      expect(result).toEqual(mockCartResponse)
    })
  })

  describe('updateItemQuantity', () => {
    it('should update item quantity', async () => {
      vi.mocked(apiClient.put).mockResolvedValue({ data: mockCartResponse })

      const request: UpdateCartItemRequest = { quantity: 5 }

      const result = await cartService.updateItemQuantity('prod-1', request)

      expect(apiClient.put).toHaveBeenCalledWith('/cart/items/prod-1', request)
      expect(result).toEqual(mockCartResponse)
    })
  })

  describe('removeItem', () => {
    it('should remove item from cart', async () => {
      const updatedCart = {
        ...mockCartResponse,
        items: [mockCartResponse.items[1]],
        subtotal: 49.99,
      }
      vi.mocked(apiClient.delete).mockResolvedValue({ data: updatedCart })

      const result = await cartService.removeItem('prod-1')

      expect(apiClient.delete).toHaveBeenCalledWith('/cart/items/prod-1')
      expect(result).toEqual(updatedCart)
    })
  })

  describe('clearCart', () => {
    it('should clear all items from cart', async () => {
      const emptyCart = { ...mockCartResponse, items: [], subtotal: 0 }
      vi.mocked(apiClient.delete).mockResolvedValue({ data: emptyCart })

      const result = await cartService.clearCart()

      expect(apiClient.delete).toHaveBeenCalledWith('/cart')
      expect(result).toEqual(emptyCart)
    })
  })

  describe('getCartItemCount', () => {
    it('should return total item count', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: { count: 3 } })

      const result = await cartService.getCartItemCount()

      expect(apiClient.get).toHaveBeenCalledWith('/cart/count')
      expect(result).toBe(3)
    })
  })

  describe('syncCart', () => {
    it('should sync local cart with server', async () => {
      const localItems = [
        { productId: 'prod-1', quantity: 2 },
        { productId: 'prod-2', quantity: 1 },
      ]
      vi.mocked(apiClient.post).mockResolvedValue({ data: mockCartResponse })

      const result = await cartService.syncCart(localItems)

      expect(apiClient.post).toHaveBeenCalledWith('/cart/sync', { items: localItems })
      expect(result).toEqual(mockCartResponse)
    })
  })

  describe('mergeGuestCart', () => {
    it('should merge guest cart with authenticated user cart', async () => {
      vi.mocked(apiClient.post).mockResolvedValue({ data: mockCartResponse })

      const result = await cartService.mergeGuestCart('guest-cart-id')

      expect(apiClient.post).toHaveBeenCalledWith('/cart/merge', {
        guestCartId: 'guest-cart-id',
      })
      expect(result).toEqual(mockCartResponse)
    })
  })
})
