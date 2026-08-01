import type { CartItemResponse } from '../api/types'
import type { CartItem } from '../stores/types'

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

export const toLocalItems = (items: CartItemResponse[]): CartItem[] =>
  items.map((item) => ({
    productId: item.productId,
    name: item.productName,
    price: item.price,
    quantity: item.quantity,
    image: item.productImageUrl ?? undefined,
    serverId: item.id,
  }))
