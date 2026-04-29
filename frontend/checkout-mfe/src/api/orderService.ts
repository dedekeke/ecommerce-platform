import apiClient from './apiClient'
import type { CreateOrderPayload, Order } from './types'

export const createOrder = async (payload: CreateOrderPayload): Promise<Order> => {
  const { data } = await apiClient.post<Order>('/orders', payload)
  return data
}

export const getOrder = async (orderId: string): Promise<Order> => {
  const { data } = await apiClient.get<Order>(`/orders/${orderId}`)
  return data
}
