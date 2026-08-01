import { useEffect, useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { cartService } from '../api/services/cartService'
import { useCartStore } from '../stores/cartStore'
import { toLocalItems } from '../stores/cartSync'

/**
 * Login transition sync (ADR phase A). When the shopper becomes authenticated:
 *  1. POST /api/cart/merge — claim the guest cart built under their verified email
 *     (server resolves the email; idempotent, best-effort).
 *  2. GET /api/cart, then push any LOCAL-only items (added while anonymous) via
 *     POST /api/cart/items — per-item failures (e.g. product now unavailable) are
 *     skipped so one bad line never blocks the rest.
 *  3. Re-GET and hydrate the shell cart store from the server, which is what the
 *     order saga will read at checkout.
 *
 * On total failure the local cart is left untouched — never wipe what the shopper
 * sees because the network blipped.
 */
export function useCartServerSync(): { synced: boolean; syncing: boolean } {
  const { isAuthenticated } = useAuth0()
  // `syncing` is derived (auth'd and not yet settled) so the effect never sets state synchronously.
  const [result, setResult] = useState<{ synced: boolean; settled: boolean }>({
    synced: false,
    settled: false,
  })

  useEffect(() => {
    if (!isAuthenticated) return

    let cancelled = false
    void (async () => {
      try {
        try {
          await cartService.mergeGuestCart()
        } catch {
          // Best-effort claim; an unresolvable email or transient failure must not block hydration.
        }

        let cart = await cartService.getCart()
        const localOnly = useCartStore
          .getState()
          .items.filter((local) => !cart.items.some((s) => s.productId === local.productId))

        if (localOnly.length > 0) {
          for (const item of localOnly) {
            try {
              await cartService.addItem({ productId: item.productId, quantity: item.quantity })
            } catch {
              // Skip items the server rejects (e.g. no longer available) — push the rest.
            }
          }
          cart = await cartService.getCart()
        }

        if (cancelled) return
        useCartStore.getState().replaceItems(toLocalItems(cart.items))
        setResult({ synced: true, settled: true })
      } catch {
        if (!cancelled) setResult({ synced: false, settled: true })
      }
    })()

    return () => {
      cancelled = true
    }
  }, [isAuthenticated])

  return { synced: result.synced, syncing: isAuthenticated && !result.settled }
}

export default useCartServerSync
