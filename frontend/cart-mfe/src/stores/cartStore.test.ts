import { describe, it, expect, beforeEach } from 'vitest'
import { useCartStore } from './cartStore'

describe('cartStore', () => {
  beforeEach(() => {
    useCartStore.setState({
      items: [],
      total: 0,
      itemCount: 0,
      promotionCode: null,
      discountAmount: null,
      promotionName: null,
    })
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

    it('should also clear any applied promotion', () => {
      useCartStore
        .getState()
        .applyPromotion({ code: 'SAVE10', discountAmount: 10, promotionName: '10 Off Sale' })
      useCartStore.getState().clearCart()
      const { promotionCode, discountAmount, promotionName } = useCartStore.getState()
      expect(promotionCode).toBeNull()
      expect(discountAmount).toBeNull()
      expect(promotionName).toBeNull()
    })
  })

  describe('applyPromotion', () => {
    it('should store the promotion code, discount amount and promotion name', () => {
      useCartStore
        .getState()
        .applyPromotion({ code: 'SAVE10', discountAmount: 10, promotionName: '10 Off Sale' })
      const { promotionCode, discountAmount, promotionName } = useCartStore.getState()
      expect(promotionCode).toBe('SAVE10')
      expect(discountAmount).toBe(10)
      expect(promotionName).toBe('10 Off Sale')
    })

    it('should overwrite a previously applied promotion', () => {
      useCartStore
        .getState()
        .applyPromotion({ code: 'SAVE10', discountAmount: 10, promotionName: '10 Off Sale' })
      useCartStore
        .getState()
        .applyPromotion({ code: 'SAVE20', discountAmount: 20, promotionName: '20 Off Sale' })
      const { promotionCode, discountAmount } = useCartStore.getState()
      expect(promotionCode).toBe('SAVE20')
      expect(discountAmount).toBe(20)
    })
  })

  describe('removePromotion', () => {
    it('should clear the applied promotion fields', () => {
      useCartStore
        .getState()
        .applyPromotion({ code: 'SAVE10', discountAmount: 10, promotionName: '10 Off Sale' })
      useCartStore.getState().removePromotion()
      const { promotionCode, discountAmount, promotionName } = useCartStore.getState()
      expect(promotionCode).toBeNull()
      expect(discountAmount).toBeNull()
      expect(promotionName).toBeNull()
    })

    it('should not affect cart items', () => {
      useCartStore.getState().addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      useCartStore
        .getState()
        .applyPromotion({ code: 'SAVE10', discountAmount: 10, promotionName: '10 Off Sale' })
      useCartStore.getState().removePromotion()
      expect(useCartStore.getState().items).toHaveLength(1)
    })
  })

  describe('side effects', () => {
    // Regression: the store must stay a pure state container. Toasts are wired at the
    // interaction layer (useCart) so they only fire on actual user actions, never on
    // no-op/internal store mutations.
    function captureToasts() {
      const events: CustomEvent[] = []
      const listener = (e: Event) => events.push(e as CustomEvent)
      window.addEventListener('ecommerce:toast', listener)
      return {
        events,
        cleanup: () => window.removeEventListener('ecommerce:toast', listener),
      }
    }

    it('should not dispatch any toast events for addItem/removeItem/updateQuantity/clearCart', () => {
      window.__ecommerceToastHost = true
      const { events, cleanup } = captureToasts()

      useCartStore.getState().addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      useCartStore.getState().updateQuantity('p1', 3)
      useCartStore.getState().updateQuantity('p1', 0)
      useCartStore.getState().addItem({ productId: 'p2', name: 'Gadget', price: 25.0 })
      useCartStore.getState().removeItem('p2')
      useCartStore
        .getState()
        .applyPromotion({ code: 'SAVE10', discountAmount: 10, promotionName: '10 Off Sale' })
      useCartStore.getState().removePromotion()
      useCartStore.getState().clearCart()

      expect(events).toHaveLength(0)
      cleanup()
      delete window.__ecommerceToastHost
    })
  })
})
