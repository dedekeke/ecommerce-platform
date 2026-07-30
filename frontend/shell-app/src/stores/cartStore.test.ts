import { describe, it, expect, beforeEach } from 'vitest'
import { useCartStore } from './cartStore'

const mockProduct = {
  productId: 'prod-1',
  name: 'Test Product',
  price: 29.99,
  image: 'https://example.com/product.jpg',
}

const mockProduct2 = {
  productId: 'prod-2',
  name: 'Another Product',
  price: 49.99,
  image: 'https://example.com/product2.jpg',
}

describe('cartStore', () => {
  beforeEach(() => {
    useCartStore.getState().clearCart()
  })

  describe('initial state', () => {
    it('should have empty items array', () => {
      const { items } = useCartStore.getState()
      expect(items).toEqual([])
    })

    it('should have zero total', () => {
      const { total } = useCartStore.getState()
      expect(total).toBe(0)
    })

    it('should have zero item count', () => {
      const { itemCount } = useCartStore.getState()
      expect(itemCount).toBe(0)
    })
  })

  describe('addItem', () => {
    it('should add a new item to cart', () => {
      useCartStore.getState().addItem(mockProduct)

      const { items } = useCartStore.getState()
      expect(items).toHaveLength(1)
      expect(items[0]).toEqual({ ...mockProduct, quantity: 1 })
    })

    it('should increment quantity if item already exists', () => {
      useCartStore.getState().addItem(mockProduct)
      useCartStore.getState().addItem(mockProduct)

      const { items } = useCartStore.getState()
      expect(items).toHaveLength(1)
      expect(items[0].quantity).toBe(2)
    })

    it('should update total when adding item', () => {
      useCartStore.getState().addItem(mockProduct)

      const { total } = useCartStore.getState()
      expect(total).toBe(29.99)
    })

    it('should update item count when adding item', () => {
      useCartStore.getState().addItem(mockProduct)
      useCartStore.getState().addItem(mockProduct)

      const { itemCount } = useCartStore.getState()
      expect(itemCount).toBe(2)
    })

    it('should handle multiple different items', () => {
      useCartStore.getState().addItem(mockProduct)
      useCartStore.getState().addItem(mockProduct2)

      const { items, total, itemCount } = useCartStore.getState()
      expect(items).toHaveLength(2)
      expect(total).toBeCloseTo(79.98, 2)
      expect(itemCount).toBe(2)
    })
  })

  describe('removeItem', () => {
    it('should remove item from cart', () => {
      useCartStore.getState().addItem(mockProduct)
      useCartStore.getState().removeItem(mockProduct.productId)

      const { items } = useCartStore.getState()
      expect(items).toHaveLength(0)
    })

    it('should update total when removing item', () => {
      useCartStore.getState().addItem(mockProduct)
      useCartStore.getState().addItem(mockProduct2)
      useCartStore.getState().removeItem(mockProduct.productId)

      const { total } = useCartStore.getState()
      expect(total).toBe(49.99)
    })

    it('should update item count when removing item', () => {
      useCartStore.getState().addItem(mockProduct)
      useCartStore.getState().addItem(mockProduct)
      useCartStore.getState().removeItem(mockProduct.productId)

      const { itemCount } = useCartStore.getState()
      expect(itemCount).toBe(0)
    })

    it('should do nothing if item not found', () => {
      useCartStore.getState().addItem(mockProduct)
      useCartStore.getState().removeItem('non-existent')

      const { items } = useCartStore.getState()
      expect(items).toHaveLength(1)
    })
  })

  describe('updateQuantity', () => {
    it('should update item quantity', () => {
      useCartStore.getState().addItem(mockProduct)
      useCartStore.getState().updateQuantity(mockProduct.productId, 5)

      const { items } = useCartStore.getState()
      expect(items[0].quantity).toBe(5)
    })

    it('should update total when quantity changes', () => {
      useCartStore.getState().addItem(mockProduct)
      useCartStore.getState().updateQuantity(mockProduct.productId, 3)

      const { total } = useCartStore.getState()
      expect(total).toBeCloseTo(89.97, 2)
    })

    it('should update item count when quantity changes', () => {
      useCartStore.getState().addItem(mockProduct)
      useCartStore.getState().updateQuantity(mockProduct.productId, 5)

      const { itemCount } = useCartStore.getState()
      expect(itemCount).toBe(5)
    })

    it('should remove item if quantity is set to 0', () => {
      useCartStore.getState().addItem(mockProduct)
      useCartStore.getState().updateQuantity(mockProduct.productId, 0)

      const { items } = useCartStore.getState()
      expect(items).toHaveLength(0)
    })

    it('should not allow negative quantities', () => {
      useCartStore.getState().addItem(mockProduct)
      useCartStore.getState().updateQuantity(mockProduct.productId, -1)

      const { items } = useCartStore.getState()
      expect(items).toHaveLength(0)
    })
  })

  describe('promotion fields', () => {
    it('should default promotion fields to null', () => {
      const { promotionCode, discountAmount, promotionName } = useCartStore.getState()
      expect(promotionCode).toBeNull()
      expect(discountAmount).toBeNull()
      expect(promotionName).toBeNull()
    })

    it('should apply and remove a promotion', () => {
      useCartStore.getState().applyPromotion({ code: 'SAVE10', discountAmount: 5, promotionName: '10 Off' })
      expect(useCartStore.getState().promotionCode).toBe('SAVE10')

      useCartStore.getState().removePromotion()
      expect(useCartStore.getState().promotionCode).toBeNull()
      expect(useCartStore.getState().discountAmount).toBeNull()
      expect(useCartStore.getState().promotionName).toBeNull()
    })

    it('should persist promotion fields in the partialized cart-storage snapshot', () => {
      useCartStore.getState().applyPromotion({ code: 'SAVE10', discountAmount: 5, promotionName: '10 Off' })
      const persisted = JSON.parse(localStorage.getItem('cart-storage') ?? '{}')
      expect(persisted.state.promotionCode).toBe('SAVE10')
      expect(persisted.state.discountAmount).toBe(5)
      expect(persisted.state.promotionName).toBe('10 Off')
    })

    // Regression: a pre-existing (version-less) cart-storage payload must survive rehydration
    // rather than being wiped by the version:1 bump introduced alongside promo fields.
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

    it('should rehydrate a v0 payload without crashing, defaulting the new promo fields', async () => {
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

    it('should rehydrate promotion fields already persisted by cart-mfe under the shared cart-storage key', async () => {
      localStorage.setItem(
        'cart-storage',
        JSON.stringify({
          state: {
            items: [],
            total: 0,
            itemCount: 0,
            promotionCode: 'SAVE10',
            discountAmount: 2.5,
            promotionName: '10% off',
          },
          version: 1,
        })
      )

      await useCartStore.persist.rehydrate()

      const { promotionCode, discountAmount, promotionName } = useCartStore.getState()
      expect(promotionCode).toBe('SAVE10')
      expect(discountAmount).toBe(2.5)
      expect(promotionName).toBe('10% off')

      localStorage.removeItem('cart-storage')
    })
  })

  describe('clearCart', () => {
    it('should remove all items', () => {
      useCartStore.getState().addItem(mockProduct)
      useCartStore.getState().addItem(mockProduct2)
      useCartStore.getState().clearCart()

      const { items } = useCartStore.getState()
      expect(items).toHaveLength(0)
    })

    it('should reset total to zero', () => {
      useCartStore.getState().addItem(mockProduct)
      useCartStore.getState().clearCart()

      const { total } = useCartStore.getState()
      expect(total).toBe(0)
    })

    it('should reset item count to zero', () => {
      useCartStore.getState().addItem(mockProduct)
      useCartStore.getState().addItem(mockProduct)
      useCartStore.getState().clearCart()

      const { itemCount } = useCartStore.getState()
      expect(itemCount).toBe(0)
    })
  })
})
