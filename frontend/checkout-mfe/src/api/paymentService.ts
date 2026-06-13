import apiClient from './apiClient'
import type { CreatePaymentIntentPayload, PaymentIntentResponse } from './types'

/**
 * Creates a PaymentIntent on the backend and returns its client_secret.
 * The client_secret is then used by Stripe.js to confirm the payment in the browser.
 */
export const createPaymentIntent = async (
  payload: CreatePaymentIntentPayload
): Promise<PaymentIntentResponse> => {
  const { data } = await apiClient.post<PaymentIntentResponse>('/payments/intents', payload)
  return data
}
