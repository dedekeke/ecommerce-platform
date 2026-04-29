import { describe, it, expect, beforeEach } from 'vitest'
import { useCartStore } from './cartStore'

describe('cartStore', () => {
  beforeEach(() => {
    useCartStore.setState({ items: [], total: 0, itemCount: 0 })
  })

  describe('addItem', () => {
    it('should add a new item with quantity 1', () => {
      useCartStore.getState().addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      const { items, total, itemCount } = useCartStore.getState()
      expect(items).toHaveLength(1)
      expect(items[0]?.quantity).toBe(1)
      expect(total).toBe(10.0)
      expect(itemCount).toBe(1)
    })

    it('should increment quantity when adding an existing item', () => {
      useCartStore.getState().addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      useCartStore.getState().addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      const { items, total, itemCount } = useCartStore.getState()
      expect(items).toHaveLength(1)
      expect(items[0]?.quantity).toBe(2)
      expect(total).toBe(20.0)
      expect(itemCount).toBe(2)
    })

    it('should add distinct items separately', () => {
      useCartStore.getState().addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      useCartStore.getState().addItem({ productId: 'p2', name: 'Gadget', price: 25.0 })
      expect(useCartStore.getState().items).toHaveLength(2)
    })
  })

  describe('removeItem', () => {
    it('should remove an item by productId', () => {
      useCartStore.getState().addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      useCartStore.getState().removeItem('p1')
      expect(useCartStore.getState().items).toHaveLength(0)
      expect(useCartStore.getState().total).toBe(0)
    })

    it('should not affect other items when removing one', () => {
      useCartStore.getState().addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      useCartStore.getState().addItem({ productId: 'p2', name: 'Gadget', price: 25.0 })
      useCartStore.getState().removeItem('p1')
      const { items } = useCartStore.getState()
      expect(items).toHaveLength(1)
      expect(items[0]?.productId).toBe('p2')
    })
  })

  describe('updateQuantity', () => {
    it('should update quantity for an existing item', () => {
      useCartStore.getState().addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      useCartStore.getState().updateQuantity('p1', 5)
      const { items, total, itemCount } = useCartStore.getState()
      expect(items[0]?.quantity).toBe(5)
      expect(total).toBe(50.0)
      expect(itemCount).toBe(5)
    })

    it('should remove item when quantity is set to 0', () => {
      useCartStore.getState().addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      useCartStore.getState().updateQuantity('p1', 0)
      expect(useCartStore.getState().items).toHaveLength(0)
    })

    it('should remove item when quantity is negative', () => {
      useCartStore.getState().addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      useCartStore.getState().updateQuantity('p1', -1)
      expect(useCartStore.getState().items).toHaveLength(0)
    })
  })

  describe('clearCart', () => {
    it('should reset all state to initial values', () => {
      useCartStore.getState().addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      useCartStore.getState().addItem({ productId: 'p2', name: 'Gadget', price: 25.0 })
      useCartStore.getState().clearCart()
      const { items, total, itemCount } = useCartStore.getState()
      expect(items).toHaveLength(0)
      expect(total).toBe(0)
      expect(itemCount).toBe(0)
    })
  })
})
