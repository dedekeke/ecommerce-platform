import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  addItemWithServerSync,
  toLocalItems,
  isCartServerSyncEnabled,
  resetCartSyncState,
  CART_SYNC_ERROR_MESSAGE,
} from './cartSync'
import { useCartStore } from './cartStore'
import { useNotificationStore } from './notificationStore'
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

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (err: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('cartSync', () => {
  beforeEach(() => {
    useCartStore.setState({ items: [], total: 0, itemCount: 0 })
    useNotificationStore.setState({ notifications: [] })
    resetCartSyncState()
    vi.clearAllMocks()
  })

  afterEach(() => {
    delete (window as { __getAuthUserId?: () => string | null }).__getAuthUserId
  })

  describe('isCartServerSyncEnabled', () => {
    it('should be false when the shell auth accessor is absent or returns null', () => {
      expect(isCartServerSyncEnabled()).toBe(false)
      window.__getAuthUserId = () => null
      expect(isCartServerSyncEnabled()).toBe(false)
    })

    it('should be true when the shell auth accessor returns a user id', () => {
      window.__getAuthUserId = () => 'auth0|u1'
      expect(isCartServerSyncEnabled()).toBe(true)
    })
  })

  describe('toLocalItems', () => {
    it('should map cart-service items to store items carrying the server id', () => {
      const cart = serverCart([
        { id: 'srv-1', productId: 'p1', productName: 'Widget', price: 10, quantity: 2 },
      ])
      expect(toLocalItems(cart.items)).toEqual([
        { productId: 'p1', name: 'Widget', price: 10, quantity: 2, image: undefined, serverId: 'srv-1' },
      ])
    })
  })

  describe('addItemWithServerSync', () => {
    it('should add locally `quantity` times and skip the server when unauthenticated', async () => {
      await addItemWithServerSync({ productId: 'p1', name: 'Mouse', price: 9.99 }, 3)

      expect(useCartStore.getState().itemCount).toBe(3)
      expect(cartService.addItem).not.toHaveBeenCalled()
    })

    it('should write through once with the full quantity and reconcile server ids when authenticated', async () => {
      window.__getAuthUserId = () => 'auth0|u1'
      vi.mocked(cartService.addItem).mockResolvedValue(
        serverCart([{ id: 'srv-1', productId: 'p1', productName: 'Mouse', price: 9.99, quantity: 3 }])
      )

      await addItemWithServerSync({ productId: 'p1', name: 'Mouse', price: 9.99 }, 3)

      expect(cartService.addItem).toHaveBeenCalledTimes(1)
      expect(cartService.addItem).toHaveBeenCalledWith({ productId: 'p1', quantity: 3 })
      expect(useCartStore.getState().items).toEqual([
        { productId: 'p1', name: 'Mouse', price: 9.99, quantity: 3, image: undefined, serverId: 'srv-1' },
      ])
    })

    it('should coalesce rapid adds of a brand-new item into ONE POST with the final quantity', async () => {
      window.__getAuthUserId = () => 'auth0|u1'
      vi.mocked(cartService.addItem).mockResolvedValue(
        serverCart([{ id: 'srv-1', productId: 'p1', productName: 'Mouse', price: 9.99, quantity: 2 }])
      )

      const pA = addItemWithServerSync({ productId: 'p1', name: 'Mouse', price: 9.99 }, 1)
      const pB = addItemWithServerSync({ productId: 'p1', name: 'Mouse', price: 9.99 }, 1)
      await Promise.all([pA, pB])

      // A second POST would make cart-service SUM the quantities (double-add inflation).
      expect(cartService.addItem).toHaveBeenCalledTimes(1)
      expect(cartService.addItem).toHaveBeenCalledWith({ productId: 'p1', quantity: 2 })
      expect(useCartStore.getState().items[0]?.quantity).toBe(2)
    })

    it('should PUT (not POST again) when a second add raced the creating POST — id from the stale response', async () => {
      window.__getAuthUserId = () => 'auth0|u1'
      const a = deferred<CartResponse>()
      vi.mocked(cartService.addItem).mockReturnValueOnce(a.promise)
      vi.mocked(cartService.updateItemQuantity).mockResolvedValue(
        serverCart([{ id: 'srv-9', productId: 'p1', productName: 'Mouse', price: 9.99, quantity: 2 }])
      )

      const pA = addItemWithServerSync({ productId: 'p1', name: 'Mouse', price: 9.99 }, 1)
      // Let the creating POST get issued (in flight), THEN the second add arrives.
      await vi.waitFor(() => expect(cartService.addItem).toHaveBeenCalledTimes(1))
      const pB = addItemWithServerSync({ productId: 'p1', name: 'Mouse', price: 9.99 }, 1)

      a.resolve(serverCart([{ id: 'srv-9', productId: 'p1', productName: 'Mouse', price: 9.99, quantity: 1 }]))
      await Promise.all([pA, pB])

      expect(cartService.addItem).toHaveBeenCalledTimes(1)
      expect(cartService.updateItemQuantity).toHaveBeenCalledTimes(1)
      expect(cartService.updateItemQuantity).toHaveBeenCalledWith('srv-9', { quantity: 2 })
      expect(useCartStore.getState().items[0]?.quantity).toBe(2)
    })

    it('should ignore a late-settling superseded response — final state reflects the LATEST add', async () => {
      window.__getAuthUserId = () => 'auth0|u1'
      useCartStore.setState({
        items: [{ productId: 'p1', name: 'Mouse', price: 9.99, quantity: 1, serverId: 'srv-1' }],
        total: 9.99,
        itemCount: 1,
      })
      const a = deferred<CartResponse>()
      const b = deferred<CartResponse>()
      vi.mocked(cartService.updateItemQuantity)
        .mockReturnValueOnce(a.promise)
        .mockReturnValueOnce(b.promise)

      const pA = addItemWithServerSync({ productId: 'p1', name: 'Mouse', price: 9.99 }, 1)
      await vi.waitFor(() => expect(cartService.updateItemQuantity).toHaveBeenCalledTimes(1))
      const pB = addItemWithServerSync({ productId: 'p1', name: 'Mouse', price: 9.99 }, 1)

      // A settles LATE with the qty-2 cart; B's optimistic 3 must survive.
      a.resolve(serverCart([{ id: 'srv-1', productId: 'p1', productName: 'Mouse', price: 9.99, quantity: 2 }]))
      await pA
      expect(useCartStore.getState().items[0]?.quantity).toBe(3)

      b.resolve(serverCart([{ id: 'srv-1', productId: 'p1', productName: 'Mouse', price: 9.99, quantity: 3 }]))
      await pB

      expect(vi.mocked(cartService.updateItemQuantity).mock.calls[1]).toEqual(['srv-1', { quantity: 3 }])
      expect(useCartStore.getState().items[0]?.quantity).toBe(3)
    })

    it('should roll back the optimistic add and raise an error notification on server failure', async () => {
      window.__getAuthUserId = () => 'auth0|u1'
      vi.mocked(cartService.addItem).mockRejectedValue(new Error('boom'))
      useCartStore.setState({
        items: [{ productId: 'p0', name: 'Kept', price: 1, quantity: 1 }],
        total: 1,
        itemCount: 1,
      })

      await addItemWithServerSync({ productId: 'p1', name: 'Mouse', price: 9.99 }, 2)

      expect(useCartStore.getState().items.map((i) => i.productId)).toEqual(['p0'])
      expect(useCartStore.getState().itemCount).toBe(1)
      expect(
        useNotificationStore
          .getState()
          .notifications.some((n) => n.type === 'error' && n.message === CART_SYNC_ERROR_MESSAGE)
      ).toBe(true)
    })
  })
})
