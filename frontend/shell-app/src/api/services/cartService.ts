import { apiClient } from '../apiClient'
import type {
  CartResponse,
  AddToCartRequest,
  UpdateCartItemRequest,
} from '../types'

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
    productId: string,
    request: UpdateCartItemRequest
  ): Promise<CartResponse> {
    const response = await apiClient.put<CartResponse>(
      `/cart/items/${productId}`,
      request
    )
    return response.data
  },

  async removeItem(productId: string): Promise<CartResponse> {
    const response = await apiClient.delete<CartResponse>(
      `/cart/items/${productId}`
    )
    return response.data
  },

  async clearCart(): Promise<CartResponse> {
    const response = await apiClient.delete<CartResponse>('/cart')
    return response.data
  },

  async getCartItemCount(): Promise<number> {
    const response = await apiClient.get<{ count: number }>('/cart/count')
    return response.data.count
  },

  async syncCart(
    items: Array<{ productId: string; quantity: number }>
  ): Promise<CartResponse> {
    const response = await apiClient.post<CartResponse>('/cart/sync', { items })
    return response.data
  },

  async mergeGuestCart(guestCartId: string): Promise<CartResponse> {
    const response = await apiClient.post<CartResponse>('/cart/merge', {
      guestCartId,
    })
    return response.data
  },
}

export default cartService
