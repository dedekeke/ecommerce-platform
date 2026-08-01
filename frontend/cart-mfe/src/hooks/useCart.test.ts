import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useCart } from './useCart'
import { useCartStore } from '../stores/cartStore'
import * as cartApi from '../api/cartService'
import type { CartResponse } from '../api/types'

vi.mock('../api/cartService', () => ({
  getCart: vi.fn(),
  addItem: vi.fn(),
  updateItemQty: vi.fn(),
  removeItem: vi.fn(),
  clearCart: vi.fn(),
}))

function captureToasts() {
  const events: CustomEvent[] = []
  const listener = (e: Event) => events.push(e as CustomEvent)
  window.addEventListener('ecommerce:toast', listener)
  return { events, cleanup: () => window.removeEventListener('ecommerce:toast', listener) }
}

const serverCart = (
  items: Array<{ id: string; productId: string; productName: string; price: number; quantity: number }>
): CartResponse => ({
  id: 'cart-1',
  userId: 'auth0|u1',
  items: items.map((i) => ({
    ...i,
    productSku: null,
    productImageUrl: null,
    subtotal: i.price * i.quantity,
  })),
  totalAmount: items.reduce((s, i) => s + i.price * i.quantity, 0),
  totalItems: items.reduce((s, i) => s + i.quantity, 0),
  status: 'ACTIVE',
})

describe('useCart', () => {
  beforeEach(() => {
    useCartStore.setState({ items: [], total: 0, itemCount: 0 })
    vi.clearAllMocks()
  })

  afterEach(() => {
    delete window.__getAuthUserId
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
      void result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
    })
    expect(result.current.items).toHaveLength(1)
    expect(result.current.total).toBe(10.0)
  })

  it('should expose removeItem action', () => {
    const { result } = renderHook(() => useCart())
    act(() => {
      void result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
    })
    act(() => {
      void result.current.removeItem('p1')
    })
    expect(result.current.items).toHaveLength(0)
  })

  it('should expose updateQuantity action', () => {
    const { result } = renderHook(() => useCart())
    act(() => {
      void result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
    })
    act(() => {
      void result.current.updateQuantity('p1', 3)
    })
    expect(result.current.items[0]?.quantity).toBe(3)
  })

  it('should expose clearCart action', () => {
    const { result } = renderHook(() => useCart())
    act(() => {
      void result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      void result.current.clearCart()
    })
    expect(result.current.items).toHaveLength(0)
  })

  it('should NOT call cart-service when unauthenticated (local-only anonymous cart)', () => {
    const { result } = renderHook(() => useCart())
    act(() => {
      void result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      void result.current.updateQuantity('p1', 2)
      void result.current.removeItem('p1')
      void result.current.clearCart()
    })
    expect(cartApi.addItem).not.toHaveBeenCalled()
    expect(cartApi.updateItemQty).not.toHaveBeenCalled()
    expect(cartApi.removeItem).not.toHaveBeenCalled()
    expect(cartApi.clearCart).not.toHaveBeenCalled()
  })

  describe('server write-through (authenticated)', () => {
    beforeEach(() => {
      window.__getAuthUserId = () => 'auth0|u1'
    })

    it('should POST the added item and reconcile serverIds from the CartResponse', async () => {
      vi.mocked(cartApi.addItem).mockResolvedValue(
        serverCart([{ id: 'srv-1', productId: 'p1', productName: 'Widget', price: 10, quantity: 1 }])
      )
      const { result } = renderHook(() => useCart())

      await act(async () => {
        await result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      })

      expect(cartApi.addItem).toHaveBeenCalledWith({ productId: 'p1', quantity: 1 })
      expect(useCartStore.getState().items).toEqual([
        { productId: 'p1', name: 'Widget', price: 10, quantity: 1, image: undefined, serverId: 'srv-1' },
      ])
    })

    it('should roll back the optimistic add when the server write fails', async () => {
      vi.mocked(cartApi.addItem).mockRejectedValue(new Error('boom'))
      const { result } = renderHook(() => useCart())

      await act(async () => {
        await result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      })

      expect(useCartStore.getState().items).toHaveLength(0)
      expect(useCartStore.getState().itemCount).toBe(0)
    })

    it('should DELETE by serverId when removing a hydrated item', async () => {
      useCartStore.setState({
        items: [{ productId: 'p1', name: 'Widget', price: 10, quantity: 2, serverId: 'srv-1' }],
        total: 20,
        itemCount: 2,
      })
      vi.mocked(cartApi.removeItem).mockResolvedValue(serverCart([]))
      const { result } = renderHook(() => useCart())

      await act(async () => {
        await result.current.removeItem('p1')
      })

      expect(cartApi.removeItem).toHaveBeenCalledWith('srv-1')
      expect(cartApi.getCart).not.toHaveBeenCalled()
      expect(useCartStore.getState().items).toHaveLength(0)
    })

    it('should resolve the serverId via GET when the local item has none', async () => {
      useCartStore.setState({
        items: [{ productId: 'p1', name: 'Widget', price: 10, quantity: 1 }],
        total: 10,
        itemCount: 1,
      })
      vi.mocked(cartApi.getCart).mockResolvedValue(
        serverCart([{ id: 'srv-9', productId: 'p1', productName: 'Widget', price: 10, quantity: 1 }])
      )
      vi.mocked(cartApi.removeItem).mockResolvedValue(serverCart([]))
      const { result } = renderHook(() => useCart())

      await act(async () => {
        await result.current.removeItem('p1')
      })

      expect(cartApi.removeItem).toHaveBeenCalledWith('srv-9')
    })

    it('should roll back the optimistic removal when the server delete fails', async () => {
      const items = [{ productId: 'p1', name: 'Widget', price: 10, quantity: 2, serverId: 'srv-1' }]
      useCartStore.setState({ items, total: 20, itemCount: 2 })
      vi.mocked(cartApi.removeItem).mockRejectedValue(new Error('boom'))
      const { result } = renderHook(() => useCart())

      await act(async () => {
        await result.current.removeItem('p1')
      })

      expect(useCartStore.getState().items).toEqual(items)
      expect(useCartStore.getState().itemCount).toBe(2)
    })

    it('should PUT the absolute quantity by serverId on updateQuantity', async () => {
      useCartStore.setState({
        items: [{ productId: 'p1', name: 'Widget', price: 10, quantity: 1, serverId: 'srv-1' }],
        total: 10,
        itemCount: 1,
      })
      vi.mocked(cartApi.updateItemQty).mockResolvedValue(
        serverCart([{ id: 'srv-1', productId: 'p1', productName: 'Widget', price: 10, quantity: 5 }])
      )
      const { result } = renderHook(() => useCart())

      await act(async () => {
        await result.current.updateQuantity('p1', 5)
      })

      expect(cartApi.updateItemQty).toHaveBeenCalledWith('srv-1', { quantity: 5 })
      expect(useCartStore.getState().items[0]?.quantity).toBe(5)
    })

    it('should DELETE the item when updateQuantity drops to 0', async () => {
      useCartStore.setState({
        items: [{ productId: 'p1', name: 'Widget', price: 10, quantity: 1, serverId: 'srv-1' }],
        total: 10,
        itemCount: 1,
      })
      vi.mocked(cartApi.removeItem).mockResolvedValue(serverCart([]))
      const { result } = renderHook(() => useCart())

      await act(async () => {
        await result.current.updateQuantity('p1', 0)
      })

      expect(cartApi.removeItem).toHaveBeenCalledWith('srv-1')
      expect(cartApi.updateItemQty).not.toHaveBeenCalled()
    })

    it('should roll back the optimistic quantity change when the server update fails', async () => {
      useCartStore.setState({
        items: [{ productId: 'p1', name: 'Widget', price: 10, quantity: 1, serverId: 'srv-1' }],
        total: 10,
        itemCount: 1,
      })
      vi.mocked(cartApi.updateItemQty).mockRejectedValue(new Error('boom'))
      const { result } = renderHook(() => useCart())

      await act(async () => {
        await result.current.updateQuantity('p1', 5)
      })

      expect(useCartStore.getState().items[0]?.quantity).toBe(1)
    })

    it('should call DELETE /cart/clear on clearCart and keep the cleared state on success', async () => {
      useCartStore.setState({
        items: [{ productId: 'p1', name: 'Widget', price: 10, quantity: 1, serverId: 'srv-1' }],
        total: 10,
        itemCount: 1,
      })
      vi.mocked(cartApi.clearCart).mockResolvedValue(undefined)
      const { result } = renderHook(() => useCart())

      await act(async () => {
        await result.current.clearCart()
      })

      expect(cartApi.clearCart).toHaveBeenCalled()
      expect(useCartStore.getState().items).toHaveLength(0)
    })

    it('should roll back items when the server clear fails', async () => {
      const items = [{ productId: 'p1', name: 'Widget', price: 10, quantity: 1, serverId: 'srv-1' }]
      useCartStore.setState({ items, total: 10, itemCount: 1 })
      vi.mocked(cartApi.clearCart).mockRejectedValue(new Error('boom'))
      const { result } = renderHook(() => useCart())

      await act(async () => {
        await result.current.clearCart()
      })

      expect(useCartStore.getState().items).toEqual(items)
    })
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
        void result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      })

      expect(events).toHaveLength(0)
      cleanup()
    })

    it('should toast "Item removed from cart" when removeItem is called', () => {
      const { result } = renderHook(() => useCart())
      act(() => {
        void result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      })
      const { events, cleanup } = captureToasts()

      act(() => {
        void result.current.removeItem('p1')
      })

      expect(events).toHaveLength(1)
      expect(events[0]?.detail).toMatchObject({ type: 'success', message: 'Item removed from cart' })
      cleanup()
    })

    it('should toast "Quantity updated" when updateQuantity is called with a positive quantity', () => {
      const { result } = renderHook(() => useCart())
      act(() => {
        void result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      })
      const { events, cleanup } = captureToasts()

      act(() => {
        void result.current.updateQuantity('p1', 3)
      })

      expect(events).toHaveLength(1)
      expect(events[0]?.detail).toMatchObject({ type: 'success', message: 'Quantity updated' })
      cleanup()
    })

    it('should toast "Item removed from cart" when updateQuantity drops the quantity to 0', () => {
      const { result } = renderHook(() => useCart())
      act(() => {
        void result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      })
      const { events, cleanup } = captureToasts()

      act(() => {
        void result.current.updateQuantity('p1', 0)
      })

      expect(events).toHaveLength(1)
      expect(events[0]?.detail).toMatchObject({ type: 'success', message: 'Item removed from cart' })
      cleanup()
    })

    it('should toast "Cart cleared" when clearCart is called', () => {
      const { result } = renderHook(() => useCart())
      act(() => {
        void result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      })
      const { events, cleanup } = captureToasts()

      act(() => {
        void result.current.clearCart()
      })

      expect(events).toHaveLength(1)
      expect(events[0]?.detail).toMatchObject({ type: 'success', message: 'Cart cleared' })
      cleanup()
    })
  })
})
