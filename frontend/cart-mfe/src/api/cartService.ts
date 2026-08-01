import apiClient from './apiClient'
import type { CartResponse, AddItemPayload, UpdateItemPayload } from './types'

export const getCart = async (): Promise<CartResponse> => {
  const { data } = await apiClient.get<CartResponse>('/cart')
  return data
}

export const addItem = async (payload: AddItemPayload): Promise<CartResponse> => {
  const { data } = await apiClient.post<CartResponse>('/cart/items', payload)
  return data
}

export const updateItemQty = async (
  itemId: string,
  payload: UpdateItemPayload
): Promise<CartResponse> => {
  const { data } = await apiClient.put<CartResponse>(`/cart/items/${itemId}`, payload)
  return data
}

export const removeItem = async (itemId: string): Promise<CartResponse> => {
  const { data } = await apiClient.delete<CartResponse>(`/cart/items/${itemId}`)
  return data
}

/** cart-service responds 204 No Content — there is no body to return. */
export const clearCart = async (): Promise<void> => {
  await apiClient.delete<void>('/cart/clear')
}
