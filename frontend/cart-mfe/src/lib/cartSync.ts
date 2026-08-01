import { useEffect, useState } from 'react'
import type { CartItemResponse, CartResponse } from '../api/types'
import type { CartItem } from '../stores/types'
import { createMutationQueue } from './cartMutationQueue'

declare global {
  interface Window {
    /** Exposed by the shell app; returns the authenticated Auth0 user id (sub) or null. */
    __getAuthUserId?: () => string | null
  }
}

/**
 * Server write-through is only possible for authenticated shoppers: every /api/cart
 * endpoint is JWT sub-keyed. Anonymous carts stay local until guest checkout pushes
 * them under X-Guest-Email (see checkout-mfe).
 */
export const isCartServerSyncEnabled = (): boolean => Boolean(window.__getAuthUserId?.())

/** Dispatched by the shell whenever its auth accessors are (re)installed. */
export const AUTH_READY_EVENT = 'ecommerce:auth-ready'

/**
 * Auth-aware variant of {@link isCartServerSyncEnabled} for render-time use. A federated
 * MFE's effects run BEFORE the shell's useExposeAuthToken effect installs
 * `window.__getAuthUserId`, so a plain render-time snapshot is a false negative on a
 * deep-linked hard reload of /cart. Re-checks when the shell announces auth
 * (AUTH_READY_EVENT) and polls briefly as a fallback for hosts that don't dispatch it.
 */
export function useCartServerSyncEnabled(): boolean {
  const [enabled, setEnabled] = useState(isCartServerSyncEnabled)

  useEffect(() => {
    if (enabled) return

    const recheck = () => {
      if (isCartServerSyncEnabled()) setEnabled(true)
    }
    window.addEventListener(AUTH_READY_EVENT, recheck)

    let attempts = 0
    const timer = window.setInterval(() => {
      attempts += 1
      if (isCartServerSyncEnabled()) {
        setEnabled(true)
        window.clearInterval(timer)
      } else if (attempts >= 10) {
        window.clearInterval(timer)
      }
    }, 300)

    return () => {
      window.removeEventListener(AUTH_READY_EVENT, recheck)
      window.clearInterval(timer)
    }
  }, [enabled])

  return enabled
}

export const toLocalItems = (items: CartItemResponse[]): CartItem[] =>
  items.map((item) => ({
    productId: item.productId,
    name: item.productName,
    price: item.price,
    quantity: item.quantity,
    image: item.productImageUrl ?? undefined,
    serverId: item.id,
  }))

/** Serializes/coalesces per-item write-throughs; baseline = last server-confirmed item state. */
export const cartMutationQueue = createMutationQueue<CartItem | undefined>()

/**
 * productId -> cart-service item id, fed by EVERY server response (even generation-stale
 * ones — the id mapping is monotonic truth, unlike quantities). Lets a queued mutation
 * PUT/DELETE a line whose creating POST it raced, instead of POSTing again (which would
 * SUM quantities server-side and inflate the line).
 */
export const knownServerIds = new Map<string, string>()

export const recordServerIds = (cart: CartResponse): void => {
  for (const item of cart.items) knownServerIds.set(item.productId, item.id)
}

export const resetCartSyncState = (): void => {
  cartMutationQueue.reset()
  knownServerIds.clear()
}
