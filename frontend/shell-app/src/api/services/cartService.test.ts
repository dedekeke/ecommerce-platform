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
  userId: 'auth0|user-1',
  items: [
    {
      id: 'item-1',
      productId: 'prod-1',
      productName: 'Test Product',
      productSku: 'SKU-1',
      productImageUrl: 'image.jpg',
      price: 99.99,
      quantity: 2,
      subtotal: 199.98,
    },
    {
      id: 'item-2',
      productId: 'prod-2',
      productName: 'Another Product',
      productSku: 'SKU-2',
      productImageUrl: 'image2.jpg',
      price: 49.99,
      quantity: 1,
      subtotal: 49.99,
    },
  ],
  totalAmount: 249.97,
  totalItems: 3,
  status: 'ACTIVE',
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
  })

  describe('addItem', () => {
    it('should POST the productId and quantity to /cart/items', async () => {
      vi.mocked(apiClient.post).mockResolvedValue({ data: mockCartResponse })
      const request: AddToCartRequest = { productId: 'prod-1', quantity: 2 }

      const result = await cartService.addItem(request)

      expect(apiClient.post).toHaveBeenCalledWith('/cart/items', request)
      expect(result).toEqual(mockCartResponse)
    })
  })

  describe('updateItemQuantity', () => {
    it('should PUT the quantity keyed by the SERVER item id', async () => {
      vi.mocked(apiClient.put).mockResolvedValue({ data: mockCartResponse })
      const request: UpdateCartItemRequest = { quantity: 5 }

      const result = await cartService.updateItemQuantity('item-1', request)

      expect(apiClient.put).toHaveBeenCalledWith('/cart/items/item-1', request)
      expect(result).toEqual(mockCartResponse)
    })
  })

  describe('removeItem', () => {
    it('should DELETE the item keyed by the SERVER item id', async () => {
      vi.mocked(apiClient.delete).mockResolvedValue({ data: mockCartResponse })

      const result = await cartService.removeItem('item-1')

      expect(apiClient.delete).toHaveBeenCalledWith('/cart/items/item-1')
      expect(result).toEqual(mockCartResponse)
    })
  })

  describe('clearCart', () => {
    it('should DELETE /cart/clear and resolve on the 204 response', async () => {
      vi.mocked(apiClient.delete).mockResolvedValue({ status: 204 })

      await expect(cartService.clearCart()).resolves.toBeUndefined()

      expect(apiClient.delete).toHaveBeenCalledWith('/cart/clear')
    })
  })

  describe('mergeGuestCart', () => {
    it('should POST /cart/merge with no body (guest email resolved server-side)', async () => {
      vi.mocked(apiClient.post).mockResolvedValue({ data: mockCartResponse })

      const result = await cartService.mergeGuestCart()

      expect(apiClient.post).toHaveBeenCalledWith('/cart/merge')
      expect(result).toEqual(mockCartResponse)
    })
  })
})
