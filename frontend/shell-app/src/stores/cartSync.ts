import { cartService } from '../api/services/cartService'
import { useCartStore } from './cartStore'
import { useNotificationStore } from './notificationStore'
import type { CartItem } from './types'
import type { CartItemResponse } from '../api/types'

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

export const CART_SYNC_ERROR_MESSAGE =
  'Could not update your cart on the server. Please try again.'

/**
 * Optimistically adds `quantity` units to the shell cart store and, when authenticated,
 * writes the add through to cart-service in one request. On success the local items are
 * reconciled from the server's CartResponse (picking up server item ids); on failure the
 * optimistic add is rolled back and an error toast is raised via the notification store.
 */
export async function addItemWithServerSync(
  item: Omit<CartItem, 'quantity'>,
  quantity = 1
): Promise<void> {
  const { addItem, items: prevItems } = useCartStore.getState()
  for (let i = 0; i < quantity; i += 1) {
    addItem(item)
  }

  if (!isCartServerSyncEnabled()) return

  try {
    const cart = await cartService.addItem({ productId: item.productId, quantity })
    useCartStore.getState().replaceItems(toLocalItems(cart.items))
  } catch {
    useCartStore.getState().replaceItems(prevItems)
    useNotificationStore.getState().addNotification({
      type: 'error',
      message: CART_SYNC_ERROR_MESSAGE,
    })
  }
}
