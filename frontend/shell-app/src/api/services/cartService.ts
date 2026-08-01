import { apiClient } from '../apiClient'
import type {
  CartResponse,
  AddToCartRequest,
  UpdateCartItemRequest,
} from '../types'

/**
 * cart-service REST client (services/cart-service CartController). All endpoints are
 * JWT sub-keyed — the server derives the cart owner from the bearer token, so no
 * userId is ever sent. Item mutations are keyed by the SERVER item id
 * (CartItemResponse.id), not the productId.
 */
export const cartService = {
  async getCart(): Promise<CartResponse> {
    const response = await apiClient.get<CartResponse>('/cart')
    return response.data
  },

  async addItem(request: AddToCartRequest): Promise<CartResponse> {
    const response = await apiClient.post<CartResponse>('/cart/items', request)
    return response.data
  },

  async updateItemQuantity(
    itemId: string,
    request: UpdateCartItemRequest
  ): Promise<CartResponse> {
    const response = await apiClient.put<CartResponse>(
      `/cart/items/${itemId}`,
      request
    )
    return response.data
  },

  async removeItem(itemId: string): Promise<CartResponse> {
    const response = await apiClient.delete<CartResponse>(
      `/cart/items/${itemId}`
    )
    return response.data
  },

  /** cart-service responds 204 No Content. */
  async clearCart(): Promise<void> {
    await apiClient.delete<void>('/cart/clear')
  },

  /**
   * Merge-on-login claim seam (`POST /api/cart/merge`): folds the guest cart built
   * under the caller's own VERIFIED email into their authenticated cart. No body —
   * the guest email is resolved server-side, never supplied by the client.
   * Idempotent; safe to call on every login.
   */
  async mergeGuestCart(): Promise<CartResponse> {
    const response = await apiClient.post<CartResponse>('/cart/merge')
    return response.data
  },
}

export default cartService
