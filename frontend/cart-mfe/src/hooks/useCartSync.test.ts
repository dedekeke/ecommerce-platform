import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { useCartSync } from './useCartSync'
import { useCartStore } from '../stores/cartStore'
import * as cartApi from '../api/cartService'
import type { CartResponse } from '../api/types'

vi.mock('../api/cartService', () => ({
  getCart: vi.fn(),
}))

const serverCart: CartResponse = {
  id: 'cart-1',
  userId: 'auth0|u1',
  items: [
    {
      id: 'srv-1',
      productId: 'p1',
      productName: 'Widget',
      productSku: null,
      productImageUrl: 'https://img.example/w.jpg',
      price: 10,
      quantity: 2,
      subtotal: 20,
    },
  ],
  totalAmount: 20,
  totalItems: 2,
  status: 'ACTIVE',
}

describe('useCartSync', () => {
  beforeEach(() => {
    useCartStore.setState({ items: [], total: 0, itemCount: 0 })
    vi.clearAllMocks()
  })

  it('should not sync when unauthenticated', () => {
    const { result } = renderHook(() => useCartSync(false))
    expect(result.current.synced).toBe(false)
    expect(result.current.syncing).toBe(false)
    expect(cartApi.getCart).not.toHaveBeenCalled()
  })

  it('should hydrate the local store from GET /cart when authenticated', async () => {
    vi.mocked(cartApi.getCart).mockResolvedValue(serverCart)
    const { result } = renderHook(() => useCartSync(true))

    await waitFor(() => expect(result.current.synced).toBe(true))

    expect(result.current.syncing).toBe(false)
    expect(useCartStore.getState().items).toEqual([
      {
        productId: 'p1',
        name: 'Widget',
        price: 10,
        quantity: 2,
        image: 'https://img.example/w.jpg',
        serverId: 'srv-1',
      },
    ])
    expect(useCartStore.getState().total).toBe(20)
    expect(useCartStore.getState().itemCount).toBe(2)
  })

  it('should replace stale local items with the server cart (server is source of truth)', async () => {
    useCartStore.setState({
      items: [{ productId: 'stale', name: 'Stale', price: 1, quantity: 1 }],
      total: 1,
      itemCount: 1,
    })
    vi.mocked(cartApi.getCart).mockResolvedValue(serverCart)
    const { result } = renderHook(() => useCartSync(true))

    await waitFor(() => expect(result.current.synced).toBe(true))

    expect(useCartStore.getState().items.map((i) => i.productId)).toEqual(['p1'])
  })

  it('should keep the local cart untouched when the fetch fails', async () => {
    useCartStore.setState({
      items: [{ productId: 'p-local', name: 'Local', price: 5, quantity: 1 }],
      total: 5,
      itemCount: 1,
    })
    vi.mocked(cartApi.getCart).mockRejectedValue(new Error('network down'))
    const { result } = renderHook(() => useCartSync(true))

    await waitFor(() => expect(result.current.syncing).toBe(false))

    expect(result.current.synced).toBe(false)
    expect(useCartStore.getState().items.map((i) => i.productId)).toEqual(['p-local'])
  })

  it('should not touch the applied promotion during hydration', async () => {
    useCartStore.setState({
      items: [],
      total: 0,
      itemCount: 0,
      promotionCode: 'SAVE10',
      discountAmount: 3,
      promotionName: 'Save 10',
    })
    vi.mocked(cartApi.getCart).mockResolvedValue(serverCart)
    const { result } = renderHook(() => useCartSync(true))

    await waitFor(() => expect(result.current.synced).toBe(true))

    expect(useCartStore.getState().promotionCode).toBe('SAVE10')
    expect(useCartStore.getState().discountAmount).toBe(3)
  })
})
