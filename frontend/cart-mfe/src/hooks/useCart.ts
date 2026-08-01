import { isAxiosError } from 'axios'
import { useCartStore } from '../stores/cartStore'
import { toast } from '../lib/toast'
import * as cartApi from '../api/cartService'
import { isCartServerSyncEnabled, toLocalItems } from '../lib/cartSync'
import type { CartItem } from '../stores/types'
import type { CartResponse } from '../api/types'

/**
 * Wires cart-store actions to UI-facing toasts and, for authenticated shoppers, writes every
 * mutation through to cart-service (ADR phase A: the server cart is the source of truth the
 * order saga reads). Mutations update the local store optimistically, then reconcile from the
 * server's CartResponse; on server failure the optimistic change is rolled back and the shared
 * apiClient interceptor surfaces the error toast. Anonymous shoppers stay local-only — guest
 * checkout pushes the cart under X-Guest-Email (see checkout-mfe).
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
  const replaceItems = useCartStore((s) => s.replaceItems)

  const reconcile = (cart: CartResponse) => replaceItems(toLocalItems(cart.items))

  // 404 = the cart/item is already gone server-side, so the optimistic local removal is
  // already the truth — treat as success instead of rolling back.
  const isGoneOnServer = (err: unknown) => isAxiosError(err) && err.response?.status === 404

  // Items added while anonymous (or before hydration) carry no serverId; fall back to a GET
  // to map productId -> cart-service item id before mutating it.
  const resolveServerId = async (prev: CartItem[], productId: string): Promise<string | null> => {
    const known = prev.find((i) => i.productId === productId)?.serverId
    if (known) return known
    const cart = await cartApi.getCart()
    return cart.items.find((i) => i.productId === productId)?.id ?? null
  }

  const addItem = (item: Omit<CartItem, 'quantity'>): Promise<void> => {
    const prev = useCartStore.getState().items
    storeAddItem(item)
    if (!isCartServerSyncEnabled()) return Promise.resolve()
    return cartApi
      .addItem({ productId: item.productId, quantity: 1 })
      .then(reconcile)
      .catch(() => replaceItems(prev))
  }

  const removeItem = (productId: string): Promise<void> => {
    const prev = useCartStore.getState().items
    storeRemoveItem(productId)
    toast.success('Item removed from cart')
    if (!isCartServerSyncEnabled()) return Promise.resolve()
    return (async () => {
      try {
        const serverId = await resolveServerId(prev, productId)
        if (!serverId) return
        reconcile(await cartApi.removeItem(serverId))
      } catch (err) {
        if (!isGoneOnServer(err)) replaceItems(prev)
      }
    })()
  }

  const updateQuantity = (productId: string, quantity: number): Promise<void> => {
    const prev = useCartStore.getState().items
    storeUpdateQuantity(productId, quantity)
    toast.success(quantity <= 0 ? 'Item removed from cart' : 'Quantity updated')
    if (!isCartServerSyncEnabled()) return Promise.resolve()
    return (async () => {
      try {
        const serverId = await resolveServerId(prev, productId)
        if (quantity <= 0) {
          if (!serverId) return
          reconcile(await cartApi.removeItem(serverId))
          return
        }
        const cart = serverId
          ? await cartApi.updateItemQty(serverId, { quantity })
          : // Locally-known but never synced: the server has quantity 0, so POSTing the
            // absolute quantity creates it correctly (cart-service sums on existing items only).
            await cartApi.addItem({ productId, quantity })
        reconcile(cart)
      } catch (err) {
        if (!isGoneOnServer(err)) replaceItems(prev)
      }
    })()
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
    storeClearCart()
    toast.success('Cart cleared')
    if (!isCartServerSyncEnabled()) return Promise.resolve()
    return cartApi.clearCart().catch((err) => {
      if (isGoneOnServer(err)) return
      replaceItems(prevItems)
      // clearCart also wiped the applied promotion — restore it with the items.
      if (prevPromotion) useCartStore.getState().applyPromotion(prevPromotion)
    })
  }

  return { items, total, itemCount, addItem, removeItem, updateQuantity, clearCart }
}
