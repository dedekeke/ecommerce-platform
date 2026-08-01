import { isAxiosError } from 'axios'
import { useCartStore } from '../stores/cartStore'
import { toast } from '../lib/toast'
import * as cartApi from '../api/cartService'
import {
  isCartServerSyncEnabled,
  cartMutationQueue as queue,
  knownServerIds,
  recordServerIds,
} from '../lib/cartSync'
import type { CartItem } from '../stores/types'
import type { CartResponse } from '../api/types'

const CLEAR_KEY = '__clear__'

const isGoneOnServer = (err: unknown) => isAxiosError(err) && err.response?.status === 404

const findLocal = (productId: string): CartItem | undefined =>
  useCartStore.getState().items.find((i) => i.productId === productId)

/** Merges the server's line for `productId` into the matching local item (never the whole store). */
const applyServerItem = (productId: string, cart: CartResponse): void => {
  const serverItem = cart.items.find((i) => i.productId === productId)
  if (!serverItem) return
  const items = useCartStore.getState().items
  if (!items.some((i) => i.productId === productId)) return
  useCartStore.getState().replaceItems(
    items.map((i) =>
      i.productId === productId
        ? { ...i, serverId: serverItem.id, price: serverItem.price, quantity: serverItem.quantity }
        : i
    )
  )
}

/** Restores only `productId` to its pre-burst state; other items keep their optimistic values. */
const restoreItem = (productId: string, baseline: CartItem | undefined): void => {
  const items = useCartStore.getState().items
  if (!baseline) {
    useCartStore.getState().replaceItems(items.filter((i) => i.productId !== productId))
    return
  }
  useCartStore.getState().replaceItems(
    items.some((i) => i.productId === productId)
      ? items.map((i) => (i.productId === productId ? baseline : i))
      : [...items, baseline]
  )
}

const fetchServerId = async (productId: string): Promise<string | null> => {
  const cart = await cartApi.getCart()
  recordServerIds(cart)
  return knownServerIds.get(productId) ?? null
}

/**
 * Converges the SERVER line for `productId` to the CURRENT local state (state-convergence,
 * not op-replay). Hardened against out-of-order optimistic writes via cartMutationQueue:
 * requests for one item are serialized, a superseded mutation skips its request entirely
 * (rapid quantity edits coalesce into one call carrying the final target), and a stale
 * settlement may record server ids but never reconciles or rolls back — only the
 * latest-issued mutation owns the store.
 */
const syncItemToServer = (productId: string, serverIdHint?: string): Promise<void> => {
  const guard = queue.begin(productId)
  return queue.enqueue(productId, async () => {
    if (guard.isStale()) return // a newer mutation for this item will sync the final state
    const local = findLocal(productId)
    try {
      let cart: CartResponse | undefined
      const known = local?.serverId ?? knownServerIds.get(productId) ?? serverIdHint
      if (!local) {
        const serverId = known ?? (await fetchServerId(productId))
        if (serverId) {
          cart = await cartApi.removeItem(serverId)
          knownServerIds.delete(productId)
        }
      } else if (known) {
        cart = await cartApi.updateItemQty(known, { quantity: local.quantity })
      } else {
        // No server line known — POST creates it with the absolute local quantity.
        cart = await cartApi.addItem({ productId, quantity: local.quantity })
      }
      if (cart) recordServerIds(cart)
      if (guard.isStale()) return
      queue.commitBaseline(productId)
      if (cart) applyServerItem(productId, cart)
    } catch (err) {
      if (isGoneOnServer(err)) {
        // Already gone server-side — the optimistic removal IS the truth.
        if (!guard.isStale()) queue.commitBaseline(productId)
        return
      }
      if (guard.isStale()) return
      const baseline = queue.takeBaseline(productId)
      if (baseline.has) restoreItem(productId, baseline.value)
    }
  })
}

/**
 * Wires cart-store actions to UI-facing toasts and, for authenticated shoppers, writes every
 * mutation through to cart-service (ADR phase A: the server cart is the source of truth the
 * order saga reads). Mutations update the local store optimistically and converge the server
 * via {@link syncItemToServer}; on failure of the latest mutation the item rolls back to its
 * last server-confirmed state and the shared apiClient interceptor surfaces the error toast.
 * Anonymous shoppers stay local-only — guest checkout pushes the cart under X-Guest-Email
 * (see checkout-mfe).
 *
 * `addItem` is intentionally untoasted here — adds originate from product-catalog-mfe, which
 * already toasts "Added to cart".
 */
export function useCart() {
  const items = useCartStore((s) => s.items)
  const total = useCartStore((s) => s.total)
  const itemCount = useCartStore((s) => s.itemCount)
  const storeAddItem = useCartStore((s) => s.addItem)
  const storeRemoveItem = useCartStore((s) => s.removeItem)
  const storeUpdateQuantity = useCartStore((s) => s.updateQuantity)
  const storeClearCart = useCartStore((s) => s.clearCart)

  /** Captures the pre-mutation item as the burst baseline; returns the serverId hint. */
  const beginWrite = (productId: string): string | undefined => {
    const prev = findLocal(productId)
    queue.rememberBaseline(productId, prev)
    return prev?.serverId
  }

  const addItem = (item: Omit<CartItem, 'quantity'>): Promise<void> => {
    if (!isCartServerSyncEnabled()) {
      storeAddItem(item)
      return Promise.resolve()
    }
    const hint = beginWrite(item.productId)
    storeAddItem(item)
    return syncItemToServer(item.productId, hint)
  }

  const removeItem = (productId: string): Promise<void> => {
    const sync = isCartServerSyncEnabled()
    const hint = sync ? beginWrite(productId) : undefined
    storeRemoveItem(productId)
    toast.success('Item removed from cart')
    return sync ? syncItemToServer(productId, hint) : Promise.resolve()
  }

  const updateQuantity = (productId: string, quantity: number): Promise<void> => {
    const sync = isCartServerSyncEnabled()
    const hint = sync ? beginWrite(productId) : undefined
    storeUpdateQuantity(productId, quantity)
    toast.success(quantity <= 0 ? 'Item removed from cart' : 'Quantity updated')
    return sync ? syncItemToServer(productId, hint) : Promise.resolve()
  }

  const clearCart = (): Promise<void> => {
    const prevState = useCartStore.getState()
    const prevItems = prevState.items
    const prevPromotion = prevState.promotionCode
      ? {
          code: prevState.promotionCode,
          discountAmount: prevState.discountAmount ?? 0,
          promotionName: prevState.promotionName,
        }
      : null
    const sync = isCartServerSyncEnabled()
    storeClearCart()
    toast.success('Cart cleared')
    if (!sync) return Promise.resolve()

    // Stale-out every in-flight per-item mutation FIRST so a late settlement can neither
    // resurrect a cleared line nor roll one back. (A mutation issued after this clear does
    // not stale the clear itself — a failing clear rolling back over it is accepted for
    // phase A: it requires re-editing within one RTT of a failing clear.)
    queue.invalidateAll()
    const guard = queue.begin(CLEAR_KEY)
    return queue.enqueue(CLEAR_KEY, async () => {
      if (guard.isStale()) return
      try {
        await cartApi.clearCart()
        knownServerIds.clear()
      } catch (err) {
        if (isGoneOnServer(err)) return
        if (guard.isStale()) return
        useCartStore.getState().replaceItems(prevItems)
        // clearCart also wiped the applied promotion — restore it with the items.
        if (prevPromotion) useCartStore.getState().applyPromotion(prevPromotion)
      }
    })
  }

  return { items, total, itemCount, addItem, removeItem, updateQuantity, clearCart }
}
