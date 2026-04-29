import { describe, it, expect, beforeEach } from 'vitest'
import { useCartStore } from './cartStore'

const seed = () => {
  useCartStore.getState().addItem({ productId: 'p1', name: 'Widget', price: 10 })
  useCartStore.getState().addItem({ productId: 'p2', name: 'Gadget', price: 25 })
}

describe('useCartStore', () => {
  beforeEach(() => {
    useCartStore.getState().clearCart()
  })

  it('should start with empty cart', () => {
    const { items, total, itemCount } = useCartStore.getState()
    expect(items).toHaveLength(0)
    expect(total).toBe(0)
    expect(itemCount).toBe(0)
  })

  it('should add a new item', () => {
    useCartStore.getState().addItem({ productId: 'p1', name: 'Widget', price: 10 })
    const { items, total, itemCount } = useCartStore.getState()
    expect(items).toHaveLength(1)
    expect(items[0].quantity).toBe(1)
    expect(total).toBe(10)
    expect(itemCount).toBe(1)
  })

  it('should increment quantity when adding the same product', () => {
    useCartStore.getState().addItem({ productId: 'p1', name: 'Widget', price: 10 })
    useCartStore.getState().addItem({ productId: 'p1', name: 'Widget', price: 10 })
    const { items, itemCount } = useCartStore.getState()
    expect(items).toHaveLength(1)
    expect(items[0].quantity).toBe(2)
    expect(itemCount).toBe(2)
  })

  it('should remove an item by productId', () => {
    seed()
    useCartStore.getState().removeItem('p1')
    const { items } = useCartStore.getState()
    expect(items).toHaveLength(1)
    expect(items[0].productId).toBe('p2')
  })

  it('should update quantity of an item', () => {
    seed()
    useCartStore.getState().updateQuantity('p1', 5)
    const { items, total } = useCartStore.getState()
    const widget = items.find((i) => i.productId === 'p1')
    expect(widget?.quantity).toBe(5)
    expect(total).toBe(5 * 10 + 25)
  })

  it('should remove item when updateQuantity is called with 0', () => {
    seed()
    useCartStore.getState().updateQuantity('p1', 0)
    const { items } = useCartStore.getState()
    expect(items.find((i) => i.productId === 'p1')).toBeUndefined()
  })

  it('should remove item when updateQuantity is called with negative value', () => {
    seed()
    useCartStore.getState().updateQuantity('p2', -1)
    const { items } = useCartStore.getState()
    expect(items.find((i) => i.productId === 'p2')).toBeUndefined()
  })

  it('should clear the cart', () => {
    seed()
    useCartStore.getState().clearCart()
    const { items, total, itemCount } = useCartStore.getState()
    expect(items).toHaveLength(0)
    expect(total).toBe(0)
    expect(itemCount).toBe(0)
  })

  it('should correctly compute itemCount for multiple items', () => {
    useCartStore.getState().addItem({ productId: 'p1', name: 'Widget', price: 10 })
    useCartStore.getState().addItem({ productId: 'p1', name: 'Widget', price: 10 })
    useCartStore.getState().addItem({ productId: 'p2', name: 'Gadget', price: 25 })
    expect(useCartStore.getState().itemCount).toBe(3)
  })
})
