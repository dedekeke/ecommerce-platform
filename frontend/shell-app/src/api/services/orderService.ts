import { apiClient } from '../apiClient'
import type {
  Order,
  PaginatedResponse,
  CreateOrderRequest,
  OrderSearchParams,
  ValidatePromotionRequest,
  ValidatePromotionResponse,
} from '../types'

export interface OrderTrackingInfo {
  orderId: string
  status: string
  trackingNumber?: string
  carrier?: string
  estimatedDelivery?: string
  events: Array<{
    timestamp: string
    status: string
    location?: string
    description: string
  }>
}

export const orderService = {
  async getOrders(
    params: OrderSearchParams = {}
  ): Promise<PaginatedResponse<Order>> {
    const response = await apiClient.get<PaginatedResponse<Order>>('/orders', {
      params,
    })
    return response.data
  },

  async getOrderById(id: string): Promise<Order> {
    const response = await apiClient.get<Order>(`/orders/${id}`)
    return response.data
  },

  async getOrderByNumber(orderNumber: string): Promise<Order> {
    const response = await apiClient.get<Order>(`/orders/number/${orderNumber}`)
    return response.data
  },

  async createOrder(request: CreateOrderRequest): Promise<Order> {
    const response = await apiClient.post<Order>('/orders', request)
    return response.data
  },

  async cancelOrder(id: string): Promise<Order> {
    const response = await apiClient.put<Order>(`/orders/${id}/cancel`)
    return response.data
  },

  async trackOrder(id: string): Promise<OrderTrackingInfo> {
    const response = await apiClient.get<OrderTrackingInfo>(
      `/orders/${id}/tracking`
    )
    return response.data
  },

  async validatePromotion(
    request: ValidatePromotionRequest
  ): Promise<ValidatePromotionResponse> {
    const response = await apiClient.post<ValidatePromotionResponse>(
      '/promotions/validate',
      request
    )
    return response.data
  },

  async reorder(orderId: string): Promise<Order> {
    const response = await apiClient.post<Order>(`/orders/${orderId}/reorder`)
    return response.data
  },
}

export default orderService
