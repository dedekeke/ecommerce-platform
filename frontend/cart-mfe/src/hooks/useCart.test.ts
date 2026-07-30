import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useCart } from './useCart'
import { useCartStore } from '../stores/cartStore'

function captureToasts() {
  const events: CustomEvent[] = []
  const listener = (e: Event) => events.push(e as CustomEvent)
  window.addEventListener('ecommerce:toast', listener)
  return { events, cleanup: () => window.removeEventListener('ecommerce:toast', listener) }
}

describe('useCart', () => {
  beforeEach(() => {
    useCartStore.setState({ items: [], total: 0, itemCount: 0 })
  })

  it('should return empty cart state initially', () => {
    const { result } = renderHook(() => useCart())
    expect(result.current.items).toHaveLength(0)
    expect(result.current.total).toBe(0)
    expect(result.current.itemCount).toBe(0)
  })

  it('should expose addItem action', () => {
    const { result } = renderHook(() => useCart())
    act(() => {
      result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
    })
    expect(result.current.items).toHaveLength(1)
    expect(result.current.total).toBe(10.0)
  })

  it('should expose removeItem action', () => {
    const { result } = renderHook(() => useCart())
    act(() => {
      result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
    })
    act(() => {
      result.current.removeItem('p1')
    })
    expect(result.current.items).toHaveLength(0)
  })

  it('should expose updateQuantity action', () => {
    const { result } = renderHook(() => useCart())
    act(() => {
      result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
    })
    act(() => {
      result.current.updateQuantity('p1', 3)
    })
    expect(result.current.items[0]?.quantity).toBe(3)
  })

  it('should expose clearCart action', () => {
    const { result } = renderHook(() => useCart())
    act(() => {
      result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      result.current.clearCart()
    })
    expect(result.current.items).toHaveLength(0)
  })

  describe('toast wiring', () => {
    beforeEach(() => {
      window.__ecommerceToastHost = true
    })
    afterEach(() => {
      delete window.__ecommerceToastHost
    })

    it('should NOT toast when addItem is called (product-catalog-mfe already toasts "Added to cart")', () => {
      const { result } = renderHook(() => useCart())
      const { events, cleanup } = captureToasts()

      act(() => {
        result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      })

      expect(events).toHaveLength(0)
      cleanup()
    })

    it('should toast "Item removed from cart" when removeItem is called', () => {
      const { result } = renderHook(() => useCart())
      act(() => {
        result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      })
      const { events, cleanup } = captureToasts()

      act(() => {
        result.current.removeItem('p1')
      })

      expect(events).toHaveLength(1)
      expect(events[0]?.detail).toMatchObject({ type: 'success', message: 'Item removed from cart' })
      cleanup()
    })

    it('should toast "Quantity updated" when updateQuantity is called with a positive quantity', () => {
      const { result } = renderHook(() => useCart())
      act(() => {
        result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      })
      const { events, cleanup } = captureToasts()

      act(() => {
        result.current.updateQuantity('p1', 3)
      })

      expect(events).toHaveLength(1)
      expect(events[0]?.detail).toMatchObject({ type: 'success', message: 'Quantity updated' })
      cleanup()
    })

    it('should toast "Item removed from cart" when updateQuantity drops the quantity to 0', () => {
      const { result } = renderHook(() => useCart())
      act(() => {
        result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      })
      const { events, cleanup } = captureToasts()

      act(() => {
        result.current.updateQuantity('p1', 0)
      })

      expect(events).toHaveLength(1)
      expect(events[0]?.detail).toMatchObject({ type: 'success', message: 'Item removed from cart' })
      cleanup()
    })

    it('should toast "Cart cleared" when clearCart is called', () => {
      const { result } = renderHook(() => useCart())
      act(() => {
        result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      })
      const { events, cleanup } = captureToasts()

      act(() => {
        result.current.clearCart()
      })

      expect(events).toHaveLength(1)
      expect(events[0]?.detail).toMatchObject({ type: 'success', message: 'Cart cleared' })
      cleanup()
    })
  })
})
