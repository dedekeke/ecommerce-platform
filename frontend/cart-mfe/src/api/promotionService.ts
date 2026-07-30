import apiClient from './apiClient'
import type { PromotionValidationRequest, PromotionValidationResponse } from './types'

/**
 * `POST /api/promotions/validate` (permitAll). Business-invalid codes come back as a normal
 * 200 with `valid: false` — only transport/server failures reject here. `skipErrorToast` is set
 * because callers (see `usePromotion`) always render their own inline error for this request,
 * for both the 200/invalid and the rejected cases, so the global toast would double-report.
 */
export const validatePromotion = async (
  payload: PromotionValidationRequest
): Promise<PromotionValidationResponse> => {
  const { data } = await apiClient.post<PromotionValidationResponse>(
    '/promotions/validate',
    payload,
    { skipErrorToast: true }
  )
  return data
}
