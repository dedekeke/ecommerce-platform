import apiClient from '../apiClient'
import type { Product } from '../../types'

/**
 * Thin wrapper around `GET /api/recommendations/product/:id`.
 *
 * The backend endpoint is gated behind the `RECOMMENDATIONS` feature flag
 * on the frontend (see `featureFlags.ts`); callers should check the flag
 * via `useFeatureFlag('RECOMMENDATIONS')` before invoking this service so
 * we don't fire a request the UI is going to discard anyway.
 */
export const recommendationService = {
  async getForProduct(productId: string, limit = 8): Promise<Product[]> {
    const response = await apiClient.get<Product[]>(
      `/recommendations/product/${productId}`,
      { params: { limit } },
    )
    return response.data
  },
}

export default recommendationService
