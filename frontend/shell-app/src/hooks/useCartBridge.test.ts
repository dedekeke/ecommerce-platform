import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { useCartBridge } from './useCartBridge'
import { useCartStore } from '../stores'
import { cartService } from '../api/services/cartService'
import type { CartResponse } from '../api/types'

vi.mock('../api/services/cartService', () => {
  const mock = {
    getCart: vi.fn(),
    addItem: vi.fn(),
    updateItemQuantity: vi.fn(),
    removeItem: vi.fn(),
    clearCart: vi.fn(),
    mergeGuestCart: vi.fn(),
  }
  return { cartService: mock, default: mock }
})

describe('useCartBridge', () => {
  beforeEach(() => {
    useCartStore.setState({ items: [], total: 0, itemCount: 0 })
    delete window.__cartBridge
    vi.clearAllMocks()
  })

  afterEach(() => {
    delete window.__cartBridge
    delete (window as { __getAuthUserId?: () => string | null }).__getAuthUserId
  })

  it('should install window.__cartBridge on mount', () => {
    renderHook(() => useCartBridge())
    expect(window.__cartBridge?.addItem).toBeTypeOf('function')
  })

  it('should remove window.__cartBridge on unmount', () => {
    const { unmount } = renderHook(() => useCartBridge())
    unmount()
    expect(window.__cartBridge).toBeUndefined()
  })

  it('should add the item to the shell cart store', () => {
    renderHook(() => useCartBridge())

    window.__cartBridge?.addItem({ productId: 'p1', name: 'Mouse', price: 9.99 })

    expect(useCartStore.getState().items).toEqual([
      { productId: 'p1', name: 'Mouse', price: 9.99, quantity: 1 },
    ])
    expect(useCartStore.getState().itemCount).toBe(1)
  })

  it('should add the item `quantity` times when quantity is greater than 1', () => {
    renderHook(() => useCartBridge())

    window.__cartBridge?.addItem({ productId: 'p1', name: 'Mouse', price: 9.99, quantity: 3 })

    expect(useCartStore.getState().itemCount).toBe(3)
    expect(useCartStore.getState().items).toEqual([
      { productId: 'p1', name: 'Mouse', price: 9.99, quantity: 3 },
    ])
  })

  it('should update the header badge count (itemCount) when items are added', () => {
    renderHook(() => useCartBridge())

    window.__cartBridge?.addItem({ productId: 'p1', name: 'Mouse', price: 9.99 })
    window.__cartBridge?.addItem({ productId: 'p2', name: 'Keyboard', price: 49.99 })

    expect(useCartStore.getState().itemCount).toBe(2)
  })

  it('should NOT call cart-service when unauthenticated (local-only anonymous cart)', () => {
    renderHook(() => useCartBridge())

    window.__cartBridge?.addItem({ productId: 'p1', name: 'Mouse', price: 9.99 })

    expect(cartService.addItem).not.toHaveBeenCalled()
  })

  it('should write the add through to cart-service and reconcile server ids when authenticated', async () => {
    window.__getAuthUserId = () => 'auth0|u1'
    const cart: CartResponse = {
      id: 'cart-1',
      userId: 'auth0|u1',
      items: [
        {
          id: 'srv-1',
          productId: 'p1',
          productName: 'Mouse',
          productSku: null,
          productImageUrl: null,
          price: 9.99,
          quantity: 2,
          subtotal: 19.98,
        },
      ],
      totalAmount: 19.98,
      totalItems: 2,
      status: 'ACTIVE',
    }
    vi.mocked(cartService.addItem).mockResolvedValue(cart)
    renderHook(() => useCartBridge())

    window.__cartBridge?.addItem({ productId: 'p1', name: 'Mouse', price: 9.99, quantity: 2 })

    await waitFor(() =>
      expect(cartService.addItem).toHaveBeenCalledWith({ productId: 'p1', quantity: 2 })
    )
    await waitFor(() => expect(useCartStore.getState().items[0]?.serverId).toBe('srv-1'))
  })

  it('should roll back the optimistic add when the server write fails', async () => {
    window.__getAuthUserId = () => 'auth0|u1'
    vi.mocked(cartService.addItem).mockRejectedValue(new Error('boom'))
    renderHook(() => useCartBridge())

    window.__cartBridge?.addItem({ productId: 'p1', name: 'Mouse', price: 9.99, quantity: 2 })

    await waitFor(() => expect(useCartStore.getState().items).toHaveLength(0))
    expect(useCartStore.getState().itemCount).toBe(0)
  })
})
