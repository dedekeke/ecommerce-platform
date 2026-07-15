import apiClient from './apiClient'
import type { CreateOrderPayload, Order } from './types'

/**
 * Submits the order. `payload.paymentMethodId` must already be a confirmed Stripe PaymentIntent
 * id (see StripeCheckout) — never raw card data. `idempotencyKey` is stable per checkout attempt
 * (see checkoutStore) so a retried submit after a network/5xx failure is deduped server-side
 * instead of creating a duplicate order.
 *
 * ASSUMPTION (see PR description): the backend order-saga contract for how a confirmed
 * PaymentIntent id is consumed on order creation was not finalized at the time of this change.
 * This is the single call site to update once that contract lands.
 */
export const createOrder = async (
  payload: CreateOrderPayload,
  idempotencyKey: string
): Promise<Order> => {
  const { data } = await apiClient.post<Order>('/orders', payload, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
  return data
}

export const getOrder = async (orderId: string): Promise<Order> => {
  const { data } = await apiClient.get<Order>(`/orders/${orderId}`)
  return data
}
