import { useEffect, useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { cartService } from '../api/services/cartService'
import { useCartStore } from '../stores/cartStore'
import { toLocalItems, recordServerIds } from '../stores/cartSync'

/**
 * Login transition sync (ADR phase A). When the shopper becomes authenticated:
 *  1. POST /api/cart/merge — claim the guest cart built under their verified email
 *     (server resolves the email; idempotent, best-effort).
 *  2. GET /api/cart, then reconcile the anonymous local cart into it:
 *     - LOCAL-only items are pushed via POST /api/cart/items;
 *     - items on BOTH sides keep max(local, server) via PUT — the server line may
 *       already include the guest-cart fold, so summing would double-count, and
 *       plain server-wins would silently drop quantity edits made while anonymous.
 *     Per-item failures (e.g. product now unavailable) are skipped so one bad line
 *     never blocks the rest.
 *  3. Re-GET and hydrate the shell cart store from the server, which is what the
 *     order saga will read at checkout.
 *
 * Dual-hydration note: cart-mfe's useCartSync hydrates ITS zustand instance onto the
 * same persisted `cart-storage` key. Both writers hydrate from the same server truth,
 * so last-writer-wins is benign for phase A; collapsing the two store instances into
 * shared-ui is the phase-B refactor.
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
        const serverByProduct = new Map(cart.items.map((s) => [s.productId, s]))
        const localItems = useCartStore.getState().items
        const localOnly = localItems.filter((local) => !serverByProduct.has(local.productId))
        // max(local, server): keep anonymous quantity edits without double-counting the fold.
        const bumps = localItems.filter((local) => {
          const server = serverByProduct.get(local.productId)
          return server !== undefined && local.quantity > server.quantity
        })

        if (localOnly.length > 0 || bumps.length > 0) {
          for (const item of localOnly) {
            try {
              await cartService.addItem({ productId: item.productId, quantity: item.quantity })
            } catch {
              // Skip items the server rejects (e.g. no longer available) — push the rest.
            }
          }
          for (const item of bumps) {
            const server = serverByProduct.get(item.productId)
            if (!server) continue
            try {
              await cartService.updateItemQuantity(server.id, { quantity: item.quantity })
            } catch {
              // Keep the server's quantity for lines the server refuses to raise.
            }
          }
          cart = await cartService.getCart()
        }

        if (cancelled) return
        recordServerIds(cart)
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
