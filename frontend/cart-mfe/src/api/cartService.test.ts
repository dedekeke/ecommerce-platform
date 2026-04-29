import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { setupServer } from 'msw/node'
import { handlers, mockCart, mockEmptyCart } from '../test/mocks'
import { getCart, addItem, updateItemQty, removeItem, clearCart } from './cartService'

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('cartService', () => {
  describe('getCart', () => {
    it('should return the current cart', async () => {
      const cart = await getCart()
      expect(cart.cartId).toBe(mockCart.cartId)
      expect(cart.items).toHaveLength(2)
      expect(cart.total).toBe(309.97)
    })
  })

  describe('addItem', () => {
    it('should add an item and return updated cart', async () => {
      const updated = await addItem({ productId: 'prod-new', quantity: 1 })
      expect(updated.items).toHaveLength(3)
    })
  })

  describe('updateItemQty', () => {
    it('should update item quantity and return updated cart', async () => {
      const updated = await updateItemQty('item-1', { quantity: 5 })
      const item = updated.items.find((i) => i.itemId === 'item-1')
      expect(item?.quantity).toBe(5)
    })
  })

  describe('removeItem', () => {
    it('should remove an item and return updated cart', async () => {
      const updated = await removeItem('item-1')
      expect(updated.items.find((i) => i.itemId === 'item-1')).toBeUndefined()
    })
  })

  describe('clearCart', () => {
    it('should clear the cart and return empty cart', async () => {
      const cleared = await clearCart()
      expect(cleared.cartId).toBe(mockEmptyCart.cartId)
      expect(cleared.items).toHaveLength(0)
      expect(cleared.total).toBe(0)
    })
  })
})
