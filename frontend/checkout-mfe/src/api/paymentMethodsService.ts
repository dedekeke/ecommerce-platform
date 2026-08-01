import apiClient from './apiClient'
import type { SavedMethodPaymentResult, SavedPaymentMethod } from './types'

/**
 * Fetches an authenticated shopper's saved Stripe payment methods (payment-service
 * `GET /payments/methods/user/{userId}`) so the Payment step can offer them as an alternative to
 * typing a new card. Never called for guests — a guest has no `userId` and therefore no saved
 * methods. Callers must treat a rejected promise (network/4xx/5xx) as "no saved methods
 * available" and fall back to the new-card flow rather than blocking checkout.
 */
export const listSavedMethods = async (userId: string): Promise<SavedPaymentMethod[]> => {
  // SavedMethodPicker renders a load failure via its own inline error Alert.
  const { data } = await apiClient.get<SavedPaymentMethod[]>(`/payments/methods/user/${userId}`, {
    skipErrorToast: true,
  })
  return data
}

/**
 * Confirms a PaymentIntent using a previously-saved payment method via the SERVER
 * (`POST /api/payments/intents/confirm-saved`), not Stripe.js. The backend verifies
 * `paymentMethodId` belongs to the authenticated caller before charging — rejecting with 403
 * otherwise — so a saved-card charge can never be forged by a client-controlled payment_method
 * id. Callers must check `result.status === 'COMPLETED'` for success; any other status (or a
 * rejected promise, e.g. 403/404/network) means no charge was completed.
 */
export const confirmSavedMethodPayment = async (
  paymentIntentId: string,
  paymentMethodId: string
): Promise<SavedMethodPaymentResult> => {
  // SavedMethodConfirmButton renders a failure via its own inline error Alert.
  const { data } = await apiClient.post<SavedMethodPaymentResult>(
    '/payments/intents/confirm-saved',
    { paymentIntentId, paymentMethodId },
    { skipErrorToast: true }
  )
  return data
}

export default listSavedMethods
