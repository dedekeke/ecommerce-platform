import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { useCartServerSync } from './useCartServerSync'
import { useCartStore } from '../stores/cartStore'
import { cartService } from '../api/services/cartService'
import type { CartResponse } from '../api/types'

vi.mock('@auth0/auth0-react', () => ({
  useAuth0: vi.fn(),
}))

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

import { useAuth0 } from '@auth0/auth0-react'

const mockUseAuth0 = vi.mocked(useAuth0)

const setAuthenticated = (isAuthenticated: boolean) =>
  mockUseAuth0.mockReturnValue({ isAuthenticated } as unknown as ReturnType<typeof useAuth0>)

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

describe('useCartServerSync', () => {
  beforeEach(() => {
    useCartStore.setState({ items: [], total: 0, itemCount: 0 })
    vi.clearAllMocks()
  })

  it('should do nothing while unauthenticated', () => {
    setAuthenticated(false)
    const { result } = renderHook(() => useCartServerSync())

    expect(result.current).toEqual({ synced: false, syncing: false })
    expect(cartService.mergeGuestCart).not.toHaveBeenCalled()
    expect(cartService.getCart).not.toHaveBeenCalled()
  })

  it('should merge the guest cart, then hydrate the store from the server on login', async () => {
    setAuthenticated(true)
    vi.mocked(cartService.mergeGuestCart).mockResolvedValue(serverCart([]))
    vi.mocked(cartService.getCart).mockResolvedValue(
      serverCart([{ id: 'srv-1', productId: 'p1', productName: 'Widget', price: 10, quantity: 2 }])
    )

    const { result } = renderHook(() => useCartServerSync())

    await waitFor(() => expect(result.current.synced).toBe(true))

    expect(cartService.mergeGuestCart).toHaveBeenCalledTimes(1)
    expect(cartService.getCart).toHaveBeenCalledTimes(1)
    expect(cartService.addItem).not.toHaveBeenCalled()
    expect(useCartStore.getState().items).toEqual([
      { productId: 'p1', name: 'Widget', price: 10, quantity: 2, image: undefined, serverId: 'srv-1' },
    ])
  })

  it('should push local-only items to the server, then hydrate from the re-fetched cart', async () => {
    setAuthenticated(true)
    useCartStore.setState({
      items: [
        { productId: 'p-local', name: 'Local Only', price: 5, quantity: 2 },
        { productId: 'p1', name: 'Widget', price: 10, quantity: 1 },
      ],
      total: 20,
      itemCount: 3,
    })
    vi.mocked(cartService.mergeGuestCart).mockResolvedValue(serverCart([]))
    vi.mocked(cartService.getCart)
      .mockResolvedValueOnce(
        serverCart([{ id: 'srv-1', productId: 'p1', productName: 'Widget', price: 10, quantity: 1 }])
      )
      .mockResolvedValueOnce(
        serverCart([
          { id: 'srv-1', productId: 'p1', productName: 'Widget', price: 10, quantity: 1 },
          { id: 'srv-2', productId: 'p-local', productName: 'Local Only', price: 5, quantity: 2 },
        ])
      )
    vi.mocked(cartService.addItem).mockResolvedValue(serverCart([]))

    const { result } = renderHook(() => useCartServerSync())

    await waitFor(() => expect(result.current.synced).toBe(true))

    expect(cartService.addItem).toHaveBeenCalledTimes(1)
    expect(cartService.addItem).toHaveBeenCalledWith({ productId: 'p-local', quantity: 2 })
    expect(cartService.getCart).toHaveBeenCalledTimes(2)
    expect(useCartStore.getState().items.map((i) => i.serverId)).toEqual(['srv-1', 'srv-2'])
  })

  it('should hydrate even when the guest-cart merge fails (best-effort claim)', async () => {
    setAuthenticated(true)
    vi.mocked(cartService.mergeGuestCart).mockRejectedValue(new Error('no verified email'))
    vi.mocked(cartService.getCart).mockResolvedValue(
      serverCart([{ id: 'srv-1', productId: 'p1', productName: 'Widget', price: 10, quantity: 1 }])
    )

    const { result } = renderHook(() => useCartServerSync())

    await waitFor(() => expect(result.current.synced).toBe(true))
    expect(useCartStore.getState().items.map((i) => i.productId)).toEqual(['p1'])
  })

  it('should keep the local cart untouched when hydration fails entirely', async () => {
    setAuthenticated(true)
    useCartStore.setState({
      items: [{ productId: 'p-local', name: 'Local', price: 5, quantity: 1 }],
      total: 5,
      itemCount: 1,
    })
    vi.mocked(cartService.mergeGuestCart).mockRejectedValue(new Error('down'))
    vi.mocked(cartService.getCart).mockRejectedValue(new Error('down'))

    const { result } = renderHook(() => useCartServerSync())

    await waitFor(() => expect(result.current.syncing).toBe(false))

    expect(result.current.synced).toBe(false)
    expect(useCartStore.getState().items.map((i) => i.productId)).toEqual(['p-local'])
  })
})
