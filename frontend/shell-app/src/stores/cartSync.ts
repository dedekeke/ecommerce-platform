import { cartService } from '../api/services/cartService'
import { useCartStore } from './cartStore'
import { useNotificationStore } from './notificationStore'
import { createMutationQueue } from './cartMutationQueue'
import type { CartItem } from './types'
import type { CartItemResponse, CartResponse } from '../api/types'

/**
 * Server write-through helpers (ADR phase A: cart-service is the source of truth the
 * order saga reads). Only authenticated shoppers can write through — every /api/cart
 * endpoint is JWT sub-keyed. Anonymous carts stay local until guest checkout pushes
 * them under X-Guest-Email (checkout-mfe) or they merge on login (useCartServerSync).
 */
export const isCartServerSyncEnabled = (): boolean => Boolean(window.__getAuthUserId?.())

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
 * PUT a line whose creating POST it raced, instead of POSTing again (which would SUM
 * quantities server-side and inflate the line).
 */
export const knownServerIds = new Map<string, string>()

export const recordServerIds = (cart: CartResponse): void => {
  for (const item of cart.items) knownServerIds.set(item.productId, item.id)
}

export const resetCartSyncState = (): void => {
  cartMutationQueue.reset()
  knownServerIds.clear()
}

export const CART_SYNC_ERROR_MESSAGE =
  'Could not update your cart on the server. Please try again.'

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

/**
 * Optimistically adds `quantity` units to the shell cart store and, when authenticated,
 * converges the SERVER line to the current local state (state-convergence, not op-replay).
 * Hardened via cartMutationQueue: requests per item are serialized, a superseded add skips
 * its request (rapid adds coalesce into one call carrying the final quantity), and a stale
 * settlement may record server ids but never reconciles or rolls back. On failure of the
 * latest mutation the item rolls back to its last server-confirmed state and an error
 * toast is raised via the notification store.
 */
export async function addItemWithServerSync(
  item: Omit<CartItem, 'quantity'>,
  quantity = 1
): Promise<void> {
  const sync = isCartServerSyncEnabled()
  const store = useCartStore.getState()
  if (sync) {
    cartMutationQueue.rememberBaseline(
      item.productId,
      store.items.find((i) => i.productId === item.productId)
    )
  }
  for (let i = 0; i < quantity; i += 1) {
    store.addItem(item)
  }
  if (!sync) return

  const guard = cartMutationQueue.begin(item.productId)
  return cartMutationQueue.enqueue(item.productId, async () => {
    if (guard.isStale()) return // a newer mutation for this item will sync the final state
    const local = useCartStore.getState().items.find((i) => i.productId === item.productId)
    if (!local) return
    try {
      const serverId = local.serverId ?? knownServerIds.get(item.productId)
      const cart = serverId
        ? await cartService.updateItemQuantity(serverId, { quantity: local.quantity })
        : await cartService.addItem({ productId: item.productId, quantity: local.quantity })
      recordServerIds(cart)
      if (guard.isStale()) return
      cartMutationQueue.commitBaseline(item.productId)
      applyServerItem(item.productId, cart)
    } catch {
      if (guard.isStale()) return
      const baseline = cartMutationQueue.takeBaseline(item.productId)
      if (baseline.has) restoreItem(item.productId, baseline.value)
      useNotificationStore.getState().addNotification({
        type: 'error',
        message: CART_SYNC_ERROR_MESSAGE,
      })
    }
  })
}
