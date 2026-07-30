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

  it('should default promotion fields to null', () => {
    const { promotionCode, discountAmount, promotionName } = useCartStore.getState()
    expect(promotionCode).toBeNull()
    expect(discountAmount).toBeNull()
    expect(promotionName).toBeNull()
  })

  // cart-mfe writes promotionCode/discountAmount/promotionName into the shared `cart-storage`
  // key when a promo is applied (see frontend/cart-mfe cartStore.applyPromotion). checkout-mfe
  // must rehydrate the same shape from that key without cart-mfe's actions ever running here.
  it('should rehydrate promotion fields persisted by cart-mfe under the shared cart-storage key', async () => {
    localStorage.setItem(
      'cart-storage',
      JSON.stringify({
        state: {
          items: [{ productId: 'p1', name: 'Widget', price: 10, quantity: 1 }],
          total: 10,
          itemCount: 1,
          promotionCode: 'SAVE10',
          discountAmount: 2.5,
          promotionName: '10% off',
        },
        version: 0,
      })
    )

    await useCartStore.persist.rehydrate()

    const { promotionCode, discountAmount, promotionName } = useCartStore.getState()
    expect(promotionCode).toBe('SAVE10')
    expect(discountAmount).toBe(2.5)
    expect(promotionName).toBe('10% off')

    localStorage.removeItem('cart-storage')
  })

  it('should persist promotion fields in the partialized cart-storage snapshot', () => {
    useCartStore.setState({ promotionCode: 'SAVE10', discountAmount: 2.5, promotionName: '10% off' })
    const persisted = JSON.parse(localStorage.getItem('cart-storage') ?? '{}')
    expect(persisted.state.promotionCode).toBe('SAVE10')
    expect(persisted.state.discountAmount).toBe(2.5)
    expect(persisted.state.promotionName).toBe('10% off')
  })

  // Regression: version:1 was introduced alongside the promo fields — a migrate passthrough
  // must be present or these older payloads would be wiped on rehydrate.
  it('should rehydrate a legacy version-less payload without crashing, defaulting the new promo fields', async () => {
    localStorage.setItem(
      'cart-storage',
      JSON.stringify({
        state: {
          items: [{ productId: 'p1', name: 'Widget', price: 10, quantity: 2 }],
          total: 20,
          itemCount: 2,
        },
      })
    )

    await expect(useCartStore.persist.rehydrate()).resolves.not.toThrow()

    const state = useCartStore.getState()
    expect(state.items).toEqual([{ productId: 'p1', name: 'Widget', price: 10, quantity: 2 }])
    expect(state.promotionCode).toBeNull()
    expect(state.discountAmount).toBeNull()
    expect(state.promotionName).toBeNull()

    localStorage.removeItem('cart-storage')
  })

  it('should rehydrate a v0 payload missing promo keys without crashing, defaulting them', async () => {
    localStorage.setItem(
      'cart-storage',
      JSON.stringify({
        state: {
          items: [{ productId: 'p1', name: 'Widget', price: 10, quantity: 1 }],
          total: 10,
          itemCount: 1,
        },
        version: 0,
      })
    )

    await expect(useCartStore.persist.rehydrate()).resolves.not.toThrow()

    const state = useCartStore.getState()
    expect(state.items).toEqual([{ productId: 'p1', name: 'Widget', price: 10, quantity: 1 }])
    expect(state.promotionCode).toBeNull()
    expect(state.discountAmount).toBeNull()
    expect(state.promotionName).toBeNull()

    localStorage.removeItem('cart-storage')
  })
})
