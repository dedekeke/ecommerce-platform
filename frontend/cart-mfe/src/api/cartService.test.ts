import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { setupServer } from 'msw/node'
import { handlers, mockCart } from '../test/mocks'
import { getCart, addItem, updateItemQty, removeItem, clearCart } from './cartService'

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('cartService', () => {
  describe('getCart', () => {
    it('should return the current cart in the cart-service wire shape', async () => {
      const cart = await getCart()
      expect(cart.id).toBe(mockCart.id)
      expect(cart.items).toHaveLength(2)
      expect(cart.totalAmount).toBe(309.97)
      expect(cart.items[0]).toMatchObject({
        id: 'item-1',
        productId: 'prod-1',
        productName: 'Wireless Headphones',
      })
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
      const item = updated.items.find((i) => i.id === 'item-1')
      expect(item?.quantity).toBe(5)
    })
  })

  describe('removeItem', () => {
    it('should remove an item and return updated cart', async () => {
      const updated = await removeItem('item-1')
      expect(updated.items.find((i) => i.id === 'item-1')).toBeUndefined()
    })
  })

  describe('clearCart', () => {
    it('should resolve on the 204 No Content response', async () => {
      await expect(clearCart()).resolves.toBeUndefined()
    })
  })
})
