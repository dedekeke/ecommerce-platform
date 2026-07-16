import apiClient from './apiClient'
import type {
  CheckoutRequestPayload,
  CheckoutResponse,
  GuestCheckoutRequestPayload,
  Order,
} from './types'

export interface CheckoutOutcome {
  response: CheckoutResponse
  /** True when the server returned 200 — an idempotent replay of an already-completed checkout. */
  isReplay: boolean
}

/**
 * Order-first checkout (services/order-service CheckoutService, PR#122): submits the shipping
 * address and lets the order-creation saga create the order plus the Stripe PaymentIntent
 * server-side. The response's `clientSecret` is what Stripe Elements confirms — this app never
 * creates its own PaymentIntent and never collects raw card data (PCI SAQ-A).
 *
 * `idempotencyKey` must be stable across retries of the same checkout attempt (see
 * checkoutStore) so a retried submit is deduped server-side instead of creating a duplicate
 * order. Callers must branch on the response:
 *  - `201` (fresh): `clientSecret` present — confirm with Stripe Elements.
 *  - `200` (replay of an already-completed key): `clientSecret` may be `null` (not persisted).
 *    Route to an order-status/confirmation view instead of mounting Elements.
 *  - `409`: a checkout with this key is still in flight — do not retry, surface a
 *    "submission in progress" message.
 *  - `502`: saga/downstream failure — transient, retried automatically by apiClient with the
 *    same Idempotency-Key.
 */
export const createOrder = async (
  payload: CheckoutRequestPayload,
  idempotencyKey: string
): Promise<CheckoutOutcome> => {
  const res = await apiClient.post<CheckoutResponse>('/orders', payload, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
  return { response: res.data, isReplay: res.status === 200 }
}

/**
 * Guest checkout — posts to the unauthenticated `POST /api/orders/guest` endpoint
 * (no `userId`; the server derives the owning identity from `email`). The
 * response contract is identical to {@link createOrder}: a fresh 201 carries the
 * `clientSecret` to confirm with Stripe Elements; a 200 is an idempotent replay.
 * The same `Idempotency-Key` discipline applies — stable across retries of one
 * attempt so a retried guest submit is deduped server-side.
 */
export const createGuestOrder = async (
  payload: GuestCheckoutRequestPayload,
  idempotencyKey: string
): Promise<CheckoutOutcome> => {
  const res = await apiClient.post<CheckoutResponse>('/orders/guest', payload, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
  return { response: res.data, isReplay: res.status === 200 }
}

export const getOrder = async (orderId: string): Promise<Order> => {
  const { data } = await apiClient.get<Order>(`/orders/${orderId}`)
  return data
}
