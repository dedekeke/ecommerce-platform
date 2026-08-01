import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import {
  isCartServerSyncEnabled,
  useCartServerSyncEnabled,
  AUTH_READY_EVENT,
  toLocalItems,
  knownServerIds,
  recordServerIds,
  resetCartSyncState,
} from './cartSync'
import type { CartResponse } from '../api/types'

describe('cartSync', () => {
  beforeEach(() => {
    resetCartSyncState()
  })

  afterEach(() => {
    delete window.__getAuthUserId
    vi.useRealTimers()
  })

  describe('isCartServerSyncEnabled', () => {
    it('should reflect the shell auth accessor', () => {
      expect(isCartServerSyncEnabled()).toBe(false)
      window.__getAuthUserId = () => null
      expect(isCartServerSyncEnabled()).toBe(false)
      window.__getAuthUserId = () => 'auth0|u1'
      expect(isCartServerSyncEnabled()).toBe(true)
    })
  })

  describe('useCartServerSyncEnabled (deep-link auth race)', () => {
    it('should be false when the accessor is absent at first render', () => {
      const { result } = renderHook(() => useCartServerSyncEnabled())
      expect(result.current).toBe(false)
    })

    it('should be true immediately when the accessor is already installed', () => {
      window.__getAuthUserId = () => 'auth0|u1'
      const { result } = renderHook(() => useCartServerSyncEnabled())
      expect(result.current).toBe(true)
    })

    it('should flip to true when the shell announces auth AFTER mount', () => {
      const { result } = renderHook(() => useCartServerSyncEnabled())
      expect(result.current).toBe(false)

      window.__getAuthUserId = () => 'auth0|u1'
      act(() => {
        window.dispatchEvent(new CustomEvent(AUTH_READY_EVENT, { detail: { userId: 'auth0|u1' } }))
      })

      expect(result.current).toBe(true)
    })

    it('should discover a late-installed accessor via the poll fallback', () => {
      vi.useFakeTimers()
      const { result } = renderHook(() => useCartServerSyncEnabled())
      expect(result.current).toBe(false)

      window.__getAuthUserId = () => 'auth0|u1'
      act(() => {
        vi.advanceTimersByTime(300)
      })

      expect(result.current).toBe(true)
    })

    it('should stay false for a genuinely anonymous shopper after the poll gives up', () => {
      vi.useFakeTimers()
      const { result } = renderHook(() => useCartServerSyncEnabled())

      act(() => {
        vi.advanceTimersByTime(10 * 300 + 300)
      })

      expect(result.current).toBe(false)
    })
  })

  describe('toLocalItems', () => {
    it('should map cart-service items to store items carrying the server id', () => {
      expect(
        toLocalItems([
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
        ])
      ).toEqual([
        {
          productId: 'p1',
          name: 'Widget',
          price: 10,
          quantity: 2,
          image: 'https://img.example/w.jpg',
          serverId: 'srv-1',
        },
      ])
    })
  })

  describe('recordServerIds', () => {
    it('should register every productId -> server item id and reset cleanly', () => {
      const cart: CartResponse = {
        id: 'c1',
        userId: 'u1',
        items: [
          {
            id: 'srv-1',
            productId: 'p1',
            productName: 'Widget',
            productSku: null,
            productImageUrl: null,
            price: 10,
            quantity: 1,
            subtotal: 10,
          },
        ],
        totalAmount: 10,
        totalItems: 1,
        status: 'ACTIVE',
      }

      recordServerIds(cart)
      expect(knownServerIds.get('p1')).toBe('srv-1')

      resetCartSyncState()
      expect(knownServerIds.size).toBe(0)
    })
  })
})
