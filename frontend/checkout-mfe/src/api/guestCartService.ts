import { isAxiosError } from 'axios'
import apiClient from './apiClient'

/**
 * Anonymous (guest) cart client — cart-service `GuestCartController` (PR#140).
 * No JWT: the cart owner is derived server-side as `guest:sha256(normalize(email))`
 * from the `X-Guest-Email` header — the SAME identity order-service's guest checkout
 * saga derives from the payload email, which is why pushing the local cart here is
 * what makes the guest order find its items (otherwise: EmptyCartException, HTTP 400).
 *
 * All requests set `skipErrorToast`: callers (CheckoutPage.submitOrder) render their
 * own inline error UI.
 */

const guestHeaders = (email: string) => ({ 'X-Guest-Email': email })

export interface GuestCartLine {
  productId: string
  quantity: number
}

export const clearGuestCart = async (email: string): Promise<void> => {
  try {
    await apiClient.delete('/cart/guest/clear', {
      headers: guestHeaders(email),
      skipErrorToast: true,
    })
  } catch (err) {
    // 404 = no active guest cart for this email yet — already "clear".
    if (isAxiosError(err) && err.response?.status === 404) return
    throw err
  }
}

export const addGuestCartItem = async (email: string, line: GuestCartLine): Promise<void> => {
  await apiClient.post('/cart/guest/items', line, {
    headers: guestHeaders(email),
    skipErrorToast: true,
  })
}

/**
 * Makes the server guest cart exactly mirror the local cart the shopper reviewed:
 * clear first (POST /items SUMS quantities on existing lines, so a leftover cart from
 * an earlier attempt would double-count), then push each line sequentially against
 * the same derived owner.
 *
 * Partial-push tradeoff: a mid-push failure leaves a PARTIAL guest cart server-side,
 * but checkout is blocked with an inline error, a retry re-runs clear-then-push
 * (self-healing), and an abandoned partial cart simply expires with cart-service's TTL.
 */
export const pushCartToGuestCart = async (
  email: string,
  items: GuestCartLine[]
): Promise<void> => {
  await clearGuestCart(email)
  for (const item of items) {
    await addGuestCartItem(email, { productId: item.productId, quantity: item.quantity })
  }
}
