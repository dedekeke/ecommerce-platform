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
