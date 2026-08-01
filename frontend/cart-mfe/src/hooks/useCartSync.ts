import { useEffect, useState } from 'react'
import { getCart } from '../api/cartService'
import { useCartStore } from '../stores/cartStore'
import { toLocalItems, recordServerIds } from '../lib/cartSync'

/**
 * Server-authoritative hydration (ADR phase A): when the shopper is authenticated,
 * pull the cart-service cart on mount and replace the local store with it — the
 * order saga builds orders from the SERVER cart, so what the shopper reviews must
 * be what the saga sees. Anonymous shoppers keep their local cart (guest checkout
 * pushes it under X-Guest-Email; see checkout-mfe).
 *
 * Dual-hydration note: the shell's useCartServerSync hydrates ITS zustand instance
 * onto the same persisted `cart-storage` key this store uses. Both writers hydrate
 * from the same server truth, so whichever lands last writes the same content —
 * last-writer-wins is benign for phase A; collapsing the two store instances into
 * shared-ui is the phase-B refactor.
 *
 * On fetch failure the local cart is left untouched: never wipe what the shopper
 * sees because the network blipped. apiClient's interceptor surfaces the error toast.
 */
export function useCartSync(isAuthenticated: boolean) {
  const replaceItems = useCartStore((s) => s.replaceItems)
  // `syncing` is derived (auth'd and not yet settled) so the effect never sets state synchronously.
  const [result, setResult] = useState<{ synced: boolean; settled: boolean }>({
    synced: false,
    settled: false,
  })

  useEffect(() => {
    if (!isAuthenticated) return

    let cancelled = false
    getCart()
      .then((cart) => {
        if (cancelled) return
        recordServerIds(cart)
        replaceItems(toLocalItems(cart.items))
        setResult({ synced: true, settled: true })
      })
      .catch(() => {
        // Keep the local cart; the shared apiClient already toasted the failure.
        if (!cancelled) setResult({ synced: false, settled: true })
      })

    return () => {
      cancelled = true
    }
  }, [isAuthenticated, replaceItems])

  return { synced: result.synced, syncing: isAuthenticated && !result.settled }
}
