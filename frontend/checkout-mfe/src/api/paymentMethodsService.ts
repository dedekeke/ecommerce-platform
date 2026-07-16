import apiClient from './apiClient'
import type { SavedPaymentMethod } from './types'

/**
 * Fetches an authenticated shopper's saved Stripe payment methods (payment-service
 * `GET /payments/methods/user/{userId}`) so the Payment step can offer them as an alternative to
 * typing a new card. Never called for guests — a guest has no `userId` and therefore no saved
 * methods. Callers must treat a rejected promise (network/4xx/5xx) as "no saved methods
 * available" and fall back to the new-card flow rather than blocking checkout.
 */
export const listSavedMethods = async (userId: string): Promise<SavedPaymentMethod[]> => {
  const { data } = await apiClient.get<SavedPaymentMethod[]>(`/payments/methods/user/${userId}`)
  return data
}

export default listSavedMethods
